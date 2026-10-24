package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;

/**
 * How far to trust a spending comparison. Money that hasn't been given a real category (typically
 * UPI payments to people) lands in OTHER_DEBIT/UNKNOWN, so lifestyle numbers silently leave it out;
 * the more of it there is, the less a "stable" or "increasing" verdict means. And if coverage shifts
 * between the baseline months and the current month (say, after the user answers the review queue),
 * a jump in a category can be re-categorization rather than real behavior change.
 *
 * <p>Purely arithmetic on existing monthly snapshots - no model, no new storage.
 */
@Service
public class AnalysisConfidenceService {

    public static final String HIGH = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final String LOW = "LOW";

    static final double LOW_UNEXPLAINED_SHARE = 0.40;
    static final double MEDIUM_UNEXPLAINED_SHARE = 0.20;
    static final double COVERAGE_SHIFT = 0.15;
    static final int MIN_BASELINE_MONTHS = 3;

    private static final Locale INDIA = Locale.of("en", "IN");

    /** @param spend total debit spending for the month (internal transfers excluded)
     *  @param unexplained the part of it with no real category */
    public record MonthCoverage(BigDecimal spend, BigDecimal unexplained) {
        double unexplainedShare() {
            if (spend.signum() <= 0) {
                return 0;
            }
            return unexplained.divide(spend, 4, RoundingMode.HALF_UP).doubleValue();
        }
    }

    public record Confidence(String level, String reason, BigDecimal unexplainedAmount, Integer explainedPercent) {
    }

    private final MemberMonthlySnapshotRepository snapshotRepository;
    private final LifestyleProperties lifestyleProperties;

    public AnalysisConfidenceService(MemberMonthlySnapshotRepository snapshotRepository,
                                      LifestyleProperties lifestyleProperties) {
        this.snapshotRepository = snapshotRepository;
        this.lifestyleProperties = lifestyleProperties;
    }

    public static MonthCoverage coverageOf(BigDecimal totalCashOutflow, BigDecimal totalOtherDebit, BigDecimal totalUnknown) {
        BigDecimal other = orZero(totalOtherDebit);
        BigDecimal unknown = orZero(totalUnknown);
        // totalCashOutflow already includes OTHER_DEBIT; UNKNOWN is tracked outside it.
        return new MonthCoverage(orZero(totalCashOutflow).add(unknown), other.add(unknown));
    }

    /** Null when the month has no spending to measure. */
    public static Integer explainedPercent(MonthCoverage coverage) {
        if (coverage.spend().signum() <= 0) {
            return null;
        }
        return (int) Math.round((1 - coverage.unexplainedShare()) * 100);
    }

    public static Confidence evaluate(MonthCoverage current, List<MonthCoverage> baselineMonthsWithData, int expectedBaselineMonths) {
        double currentShare = current.unexplainedShare();
        int months = baselineMonthsWithData.size();
        double baselineShare = baselineMonthsWithData.stream()
                .mapToDouble(MonthCoverage::unexplainedShare).average().orElse(0);
        double worst = Math.max(currentShare, baselineShare);

        String level;
        String reason;
        if (months < MIN_BASELINE_MONTHS) {
            level = LOW;
            reason = "Only " + months + (months == 1 ? " month" : " months")
                    + " of history to compare against - this will firm up as more months are added.";
        } else if (worst > LOW_UNEXPLAINED_SHARE) {
            level = LOW;
            reason = unexplainedReason(worst, current);
        } else if (worst > MEDIUM_UNEXPLAINED_SHARE) {
            level = MEDIUM;
            reason = unexplainedReason(worst, current);
        } else if (Math.abs(currentShare - baselineShare) > COVERAGE_SHIFT) {
            level = MEDIUM;
            reason = "How much of your spending is categorized changed between this month and your usual months, "
                    + "so part of any change may be re-categorization rather than real spending.";
        } else if (months < expectedBaselineMonths) {
            level = MEDIUM;
            reason = "Based on " + months + " months of history (" + expectedBaselineMonths + " is ideal).";
        } else {
            level = HIGH;
            reason = "Most of your spending is categorized across " + months + " months of history.";
        }
        return new Confidence(level, reason, current.unexplained(), explainedPercent(current));
    }

    public Confidence forLifestyle(java.util.UUID memberId, YearMonth month) {
        MonthCoverage current = snapshotRepository
                .findByMemberIdAndYearAndMonth(memberId, month.getYear(), month.getMonthValue())
                .map(AnalysisConfidenceService::coverageOf)
                .orElse(new MonthCoverage(BigDecimal.ZERO, BigDecimal.ZERO));

        int expected = lifestyleProperties.getBaselineMonths();
        List<MonthCoverage> baseline = new ArrayList<>();
        for (int monthsBack = 1; monthsBack <= expected; monthsBack++) {
            YearMonth prior = month.minusMonths(monthsBack);
            snapshotRepository.findByMemberIdAndYearAndMonth(memberId, prior.getYear(), prior.getMonthValue())
                    .map(AnalysisConfidenceService::coverageOf)
                    .filter(coverage -> coverage.spend().signum() > 0)
                    .ifPresent(baseline::add);
        }
        return evaluate(current, baseline, expected);
    }

    private static MonthCoverage coverageOf(MemberMonthlySnapshot snapshot) {
        return coverageOf(snapshot.getTotalCashOutflow(), snapshot.getTotalOtherDebit(), snapshot.getTotalUnknown());
    }

    private static String unexplainedReason(double share, MonthCoverage current) {
        return "About " + Math.round(share * 100) + "% of your spending isn't categorized yet"
                + (current.unexplained().signum() > 0
                        ? " (" + formatInr(current.unexplained()) + " this month)" : "")
                + ", so this comparison may miss real changes.";
    }

    private static String formatInr(BigDecimal amount) {
        return "₹" + NumberFormat.getIntegerInstance(INDIA).format(amount.setScale(0, RoundingMode.HALF_UP));
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
