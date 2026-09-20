package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.entity.LifestyleBaseline;
import com.prospr.app.entity.LifestyleInsight;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.repository.LifestyleBaselineRepository;
import com.prospr.app.repository.LifestyleInsightRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;

/**
 * Compares the current month's spend per category against its baseline and keeps
 * {@code lifestyle_insight} rows in sync. There is at most one row per
 * (member, year, month, category, insightType), enforced by a unique constraint and by always
 * updating an existing row in place rather than inserting a second one.
 *
 * Current-month handling: the current calendar month is very likely partial (e.g. "day 23 of 31").
 * Comparing 23 days of spend against a full multi-month average would unfairly flag normal spending
 * as a spike. So the baseline used to compute differenceAmount/increasePercentage/severity is prorated
 * to "expected spend by today" (baselineAmount * dayOfMonth / daysInMonth) when the month isn't yet
 * complete; the raw, full-month baseline is still stored/returned as-is for display ("Normal:
 * Rs4,500"). Once the month completes, prorated == raw, so the two conventions converge and match
 * the worked examples in the product spec exactly.
 */
@Service
public class LifestyleInsightService {

    private static final Logger log = LoggerFactory.getLogger(LifestyleInsightService.class);
    private static final String INSIGHT_TYPE_CATEGORY_SPIKE = "CATEGORY_SPIKE";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_RESOLVED = "RESOLVED";

    private final MemberMonthlySummaryRepository summaryRepository;
    private final LifestyleBaselineRepository baselineRepository;
    private final LifestyleInsightRepository insightRepository;
    private final LifestyleCreepAnalyzer analyzer;
    private final LifestyleCategoryCatalog catalog;
    private final LifestyleProperties properties;

    public LifestyleInsightService(MemberMonthlySummaryRepository summaryRepository,
                                    LifestyleBaselineRepository baselineRepository,
                                    LifestyleInsightRepository insightRepository,
                                    LifestyleCreepAnalyzer analyzer,
                                    LifestyleCategoryCatalog catalog,
                                    LifestyleProperties properties) {
        this.summaryRepository = summaryRepository;
        this.baselineRepository = baselineRepository;
        this.insightRepository = insightRepository;
        this.analyzer = analyzer;
        this.catalog = catalog;
        this.properties = properties;
    }

    public void recomputeForMember(Member member, YearMonth currentMonth, LocalDate today) {
        List<LifestyleBaseline> baselines = baselineRepository.findByMemberId(member.getId());
        int dayOfMonth = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();
        boolean isMonthToDate = dayOfMonth < daysInMonth;

        for (LifestyleBaseline baseline : baselines) {
            if (!catalog.isLifestyleCategory(baseline.getCategory())) {
                continue;
            }
            recomputeCategory(member, currentMonth, baseline, dayOfMonth, daysInMonth, isMonthToDate);
        }
    }

    private void recomputeCategory(Member member, YearMonth currentMonth, LifestyleBaseline baseline,
                                    int dayOfMonth, int daysInMonth, boolean isMonthToDate) {
        String category = baseline.getCategory();

        BigDecimal currentAmount = summaryRepository
                .findByMemberIdAndYearAndMonthAndCategory(member.getId(), currentMonth.getYear(), currentMonth.getMonthValue(), category)
                .map(MemberMonthlySummary::getTotalAmount)
                .orElse(BigDecimal.ZERO);

        BigDecimal comparisonBaseline = baseline.getBaselineAmount();
        if (isMonthToDate && comparisonBaseline.compareTo(BigDecimal.ZERO) > 0) {
            comparisonBaseline = comparisonBaseline
                    .multiply(BigDecimal.valueOf(dayOfMonth))
                    .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
        }

        LifestyleCreepAnalyzer.AnalysisResult result = analyzer.analyze(comparisonBaseline, currentAmount);

        Optional<LifestyleInsight> existing = insightRepository.findByMemberIdAndYearAndMonthAndCategoryAndInsightType(
                member.getId(), currentMonth.getYear(), currentMonth.getMonthValue(), category, INSIGHT_TYPE_CATEGORY_SPIKE);

        if (!result.insightWorthy()) {
            existing.filter(insight -> STATUS_ACTIVE.equals(insight.getStatus()))
                    .ifPresent(insight -> {
                        insight.setStatus(STATUS_RESOLVED);
                        insightRepository.save(insight);
                    });
            return;
        }

        LifestyleInsight insight = existing.orElseGet(() -> LifestyleInsight.builder()
                .member(member)
                .familyId(member.getFamily() != null ? member.getFamily().getId() : null)
                .year(currentMonth.getYear())
                .month(currentMonth.getMonthValue())
                .category(category)
                .insightType(INSIGHT_TYPE_CATEGORY_SPIKE)
                .build());

        insight.setBaselineAmount(baseline.getBaselineAmount());
        insight.setCurrentAmount(currentAmount);
        insight.setDifferenceAmount(result.differenceAmount());
        insight.setIncreasePercentage(result.increasePercentage());
        insight.setSeverity(result.severity().name());
        insight.setStatus(STATUS_ACTIVE);
        insight.setMessage(buildMessage(category, result));
        insightRepository.save(insight);

        log.info("Lifestyle insight for member '{}' category '{}': severity={} increase={}%",
                member.getId(), category, result.severity(), result.increasePercentage());
    }

    private String buildMessage(String category, LifestyleCreepAnalyzer.AnalysisResult result) {
        String label = catalog.label(category);
        long roundedPercent = Math.round(result.increasePercentage());
        return "Your " + label + " expenses are " + roundedPercent
                + "% higher than your average spending over the previous " + properties.getBaselineMonths()
                + " months.";
    }
}
