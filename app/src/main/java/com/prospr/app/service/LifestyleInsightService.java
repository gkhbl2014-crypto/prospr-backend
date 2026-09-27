package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.entity.LifestyleBaseline;
import com.prospr.app.entity.LifestyleInsight;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.entity.RecurringExpense;
import com.prospr.app.repository.LifestyleBaselineRepository;
import com.prospr.app.repository.LifestyleInsightRepository;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.RecurringExpenseRepository;

/**
 * Builds ONE overall Lifestyle Creep summary per member/month - not one insight per category. Every
 * recompute produces a real, evidence-based summary (even when spending is perfectly normal), instead
 * of the old design's silent "nothing to show" whenever a strict double-gate wasn't cleared.
 *
 * <p>Pipeline: sum the member's per-category baselines/current-month spend (from {@link
 * LifestyleBaselineService}/{@link MemberMonthlySummaryRepository}) into one overall total, run it
 * through {@link LifestyleCreepAnalyzer} for the diff/percentage, then {@link LifestyleTrendEvaluator}
 * for the STABLE/WATCH/INCREASING state - checking the current month plus up to {@value
 * #TREND_LOOKBACK_MONTHS} prior months (all against the same baseline) to tell a one-off spike apart
 * from a sustained increase. A new discretionary {@link RecurringExpense} (one whose {@code
 * firstSeenDate} postdates the baseline window) and an income-vs-spending comparison (only when
 * income is reliably known for every relevant month) are folded in as extra evidence when relevant.
 *
 * <p>Reuses the existing {@code lifestyle_insight} table for the one summary row per member/month via
 * the sentinel {@link #CATEGORY_OVERALL}/{@link #INSIGHT_TYPE_SUMMARY} key - see the class javadoc on
 * {@link LifestyleInsight} for why no new table was needed.
 */
@Service
public class LifestyleInsightService {

    private static final Logger log = LoggerFactory.getLogger(LifestyleInsightService.class);

    public static final String CATEGORY_OVERALL = "OVERALL";
    public static final String INSIGHT_TYPE_SUMMARY = "LIFESTYLE_CREEP_SUMMARY";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String RECURRING_STATUS_ACTIVE = "ACTIVE";
    /** Current month + this many prior months checked for the elevated-streak calculation. */
    private static final int TREND_LOOKBACK_MONTHS = 2;
    private static final int TOP_CATEGORY_COUNT = 2;
    private static final Locale INDIA = Locale.of("en", "IN");

    private final MemberMonthlySummaryRepository summaryRepository;
    private final LifestyleBaselineRepository baselineRepository;
    private final LifestyleInsightRepository insightRepository;
    private final MemberMonthlySnapshotRepository snapshotRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final LifestyleCreepAnalyzer analyzer;
    private final LifestyleTrendEvaluator trendEvaluator;
    private final LifestyleCategoryCatalog catalog;
    private final LifestyleProperties properties;

    public LifestyleInsightService(MemberMonthlySummaryRepository summaryRepository,
                                    LifestyleBaselineRepository baselineRepository,
                                    LifestyleInsightRepository insightRepository,
                                    MemberMonthlySnapshotRepository snapshotRepository,
                                    RecurringExpenseRepository recurringExpenseRepository,
                                    LifestyleCreepAnalyzer analyzer,
                                    LifestyleTrendEvaluator trendEvaluator,
                                    LifestyleCategoryCatalog catalog,
                                    LifestyleProperties properties) {
        this.summaryRepository = summaryRepository;
        this.baselineRepository = baselineRepository;
        this.insightRepository = insightRepository;
        this.snapshotRepository = snapshotRepository;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.analyzer = analyzer;
        this.trendEvaluator = trendEvaluator;
        this.catalog = catalog;
        this.properties = properties;
    }

    private record CategoryFigures(String category, BigDecimal currentAmount, BigDecimal comparisonBaseline) {
        BigDecimal difference() {
            return currentAmount.subtract(comparisonBaseline);
        }
    }

    record NewRecurringExpense(String label, BigDecimal amount) {
    }

    public void recomputeForMember(Member member, YearMonth currentMonth, LocalDate today) {
        List<LifestyleBaseline> baselines = baselineRepository.findByMemberId(member.getId()).stream()
                .filter(b -> catalog.isLifestyleCategory(b.getCategory()))
                .toList();
        if (baselines.isEmpty()) {
            return;
        }

        int dayOfMonth = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();
        boolean isMonthToDate = dayOfMonth < daysInMonth;

        List<CategoryFigures> figures = new ArrayList<>();
        for (LifestyleBaseline baseline : baselines) {
            BigDecimal currentAmount = amountFor(member, currentMonth, baseline.getCategory());
            BigDecimal comparisonBaseline = prorate(baseline.getBaselineAmount(), isMonthToDate, dayOfMonth, daysInMonth);
            figures.add(new CategoryFigures(baseline.getCategory(), currentAmount, comparisonBaseline));
        }

        BigDecimal overallCurrent = sum(figures.stream().map(CategoryFigures::currentAmount));
        BigDecimal overallComparisonBaseline = sum(figures.stream().map(CategoryFigures::comparisonBaseline));
        BigDecimal overallRawBaseline = sum(baselines.stream().map(LifestyleBaseline::getBaselineAmount));

        LifestyleCreepAnalyzer.AnalysisResult currentResult = analyzer.analyze(overallComparisonBaseline, overallCurrent);
        LifestyleTrendEvaluator.TrendResult trend = evaluateTrend(member, currentMonth, baselines, overallRawBaseline, currentResult);

        List<CategoryFigures> increased = figures.stream()
                .filter(f -> f.difference().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(CategoryFigures::difference).reversed())
                .limit(TOP_CATEGORY_COUNT)
                .toList();
        List<CategoryFigures> stableCategories = figures.stream()
                .filter(f -> !increased.contains(f))
                .sorted(Comparator.comparing(CategoryFigures::currentAmount).reversed())
                .limit(TOP_CATEGORY_COUNT)
                .toList();

        NewRecurringExpense newRecurring = trend.state() == LifestyleTrendEvaluator.State.STABLE
                ? null : findNewRecurringExpense(member, currentMonth);
        Double incomeGrowthPercent = trend.state() == LifestyleTrendEvaluator.State.STABLE
                ? null : computeIncomeGrowthPercent(member, currentMonth);

        String message = buildMessage(trend, currentResult, overallCurrent, overallRawBaseline,
                increased, stableCategories, newRecurring, incomeGrowthPercent);

        upsert(member, currentMonth, trend, currentResult, overallCurrent, overallRawBaseline,
                increased, newRecurring, incomeGrowthPercent, message);

        log.info("Lifestyle creep summary for member '{}': state={} streak={} increase={}%",
                member.getId(), trend.state(), trend.consecutiveElevatedMonths(), currentResult.increasePercentage());
    }

    /** Checks the current month plus up to {@value #TREND_LOOKBACK_MONTHS} prior months (all fully
     *  within the baseline-history window already confirmed available by {@link LifestyleBaselineService}),
     *  each against the SAME overall baseline, to build the elevated-streak. */
    private LifestyleTrendEvaluator.TrendResult evaluateTrend(Member member, YearMonth currentMonth,
                                                               List<LifestyleBaseline> baselines,
                                                               BigDecimal overallRawBaseline,
                                                               LifestyleCreepAnalyzer.AnalysisResult currentResult) {
        List<Boolean> elevatedNewestFirst = new ArrayList<>();
        elevatedNewestFirst.add(trendEvaluator.isElevated(currentResult));

        int lookback = Math.min(TREND_LOOKBACK_MONTHS, Math.max(0, properties.getBaselineMonths() - 1));
        for (int monthsBack = 1; monthsBack <= lookback; monthsBack++) {
            YearMonth priorMonth = currentMonth.minusMonths(monthsBack);
            BigDecimal priorTotal = sum(baselines.stream().map(b -> amountFor(member, priorMonth, b.getCategory())));
            LifestyleCreepAnalyzer.AnalysisResult priorResult = analyzer.analyze(overallRawBaseline, priorTotal);
            elevatedNewestFirst.add(trendEvaluator.isElevated(priorResult));
        }

        return trendEvaluator.evaluate(elevatedNewestFirst);
    }

    private NewRecurringExpense findNewRecurringExpense(Member member, YearMonth currentMonth) {
        LocalDate baselineWindowStart = currentMonth.minusMonths(properties.getBaselineMonths()).atDay(1);
        return recurringExpenseRepository.findByMemberIdOrderByTypicalAmountDesc(member.getId()).stream()
                .filter(r -> RECURRING_STATUS_ACTIVE.equals(r.getStatus()))
                .filter(r -> catalog.isLifestyleCategory(r.getCategory()))
                .filter(r -> r.getFirstSeenDate() != null && r.getFirstSeenDate().isAfter(baselineWindowStart))
                .findFirst()
                .map(r -> new NewRecurringExpense(merchantLabel(r), r.getTypicalAmount()))
                .orElse(null);
    }

    private String merchantLabel(RecurringExpense recurringExpense) {
        String key = recurringExpense.getMerchantKey();
        if (key == null || key.isBlank()) {
            return catalog.label(recurringExpense.getCategory());
        }
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }

    /** Only returns a value when income is reliably known (a real snapshot with positive totalIncome)
     *  for the current month AND every baseline month - if even one is missing/zero/unreliable, this
     *  returns null and the caller omits the income comparison entirely rather than guessing. */
    private Double computeIncomeGrowthPercent(Member member, YearMonth currentMonth) {
        BigDecimal currentIncome = reliableIncome(member, currentMonth);
        if (currentIncome == null) {
            return null;
        }

        List<BigDecimal> baselineIncomes = new ArrayList<>();
        for (int monthsBack = properties.getBaselineMonths(); monthsBack >= 1; monthsBack--) {
            BigDecimal income = reliableIncome(member, currentMonth.minusMonths(monthsBack));
            if (income == null) {
                return null;
            }
            baselineIncomes.add(income);
        }

        BigDecimal baselineAverage = sum(baselineIncomes.stream())
                .divide(BigDecimal.valueOf(baselineIncomes.size()), 6, RoundingMode.HALF_UP);
        return currentIncome.subtract(baselineAverage)
                .divide(baselineAverage, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private BigDecimal reliableIncome(Member member, YearMonth month) {
        Optional<MemberMonthlySnapshot> snapshot = snapshotRepository
                .findByMemberIdAndYearAndMonth(member.getId(), month.getYear(), month.getMonthValue());
        if (snapshot.isEmpty() || snapshot.get().getTotalIncome().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return snapshot.get().getTotalIncome();
    }

    private String buildMessage(LifestyleTrendEvaluator.TrendResult trend, LifestyleCreepAnalyzer.AnalysisResult overallResult,
                                 BigDecimal overallCurrent, BigDecimal overallBaseline, List<CategoryFigures> increased,
                                 List<CategoryFigures> stableCategories, NewRecurringExpense newRecurring,
                                 Double incomeGrowthPercent) {
        List<String> paragraphs = new ArrayList<>();

        paragraphs.add(switch (trend.state()) {
            case STABLE -> "Your discretionary spending is stable.";
            case WATCH -> "Your lifestyle spending is starting to rise.";
            case INCREASING -> "Your lifestyle spending has been rising for "
                    + trend.consecutiveElevatedMonths() + " months in a row.";
        });

        paragraphs.add("You spent " + formatInr(overallCurrent) + " this month compared with your "
                + properties.getBaselineMonths() + "-month average of " + formatInr(overallBaseline) + ".");

        if (trend.state() == LifestyleTrendEvaluator.State.STABLE) {
            if (!stableCategories.isEmpty()) {
                String names = stableCategories.stream().map(f -> catalog.label(f.category()))
                        .collect(Collectors.joining(" and "));
                String rangeWord = stableCategories.size() > 1 ? "ranges" : "range";
                String possessive = stableCategories.size() > 1 ? "their" : "its";
                paragraphs.add(names + " were also within " + possessive + " usual " + rangeWord + ".");
            }
            paragraphs.add("No significant lifestyle creep detected.");
        } else {
            if (!increased.isEmpty()) {
                StringBuilder sb = new StringBuilder("The biggest increases were:\n");
                for (CategoryFigures f : increased) {
                    sb.append("- ").append(catalog.label(f.category())).append(": +")
                            .append(formatInr(f.difference())).append('\n');
                }
                paragraphs.add(sb.toString().trim());
            }
            if (newRecurring != null) {
                paragraphs.add(formatInr(newRecurring.amount()) + " of this comes from a new recurring expense ("
                        + newRecurring.label() + ") that wasn't present in your previous baseline.");
            }
            paragraphs.add(trend.state() == LifestyleTrendEvaluator.State.INCREASING
                    ? "This is the " + ordinal(trend.consecutiveElevatedMonths())
                            + " month of increased discretionary spending - a sustained pattern, not just a one-off."
                    : "This is the first month of increased discretionary spending - it may just be a one-time change.");
        }

        if (incomeGrowthPercent != null && overallResult.increasePercentage() != null) {
            paragraphs.add("Your income grew " + Math.round(incomeGrowthPercent)
                    + "% over the same period, compared with " + Math.round(overallResult.increasePercentage())
                    + "% growth in discretionary spending.");
        }

        return String.join("\n\n", paragraphs);
    }

    private void upsert(Member member, YearMonth currentMonth, LifestyleTrendEvaluator.TrendResult trend,
                         LifestyleCreepAnalyzer.AnalysisResult result, BigDecimal overallCurrent, BigDecimal overallBaseline,
                         List<CategoryFigures> increased, NewRecurringExpense newRecurring, Double incomeGrowthPercent,
                         String message) {
        LifestyleInsight insight = insightRepository.findByMemberIdAndYearAndMonthAndCategoryAndInsightType(
                        member.getId(), currentMonth.getYear(), currentMonth.getMonthValue(), CATEGORY_OVERALL, INSIGHT_TYPE_SUMMARY)
                .orElseGet(() -> LifestyleInsight.builder()
                        .member(member)
                        .familyId(member.getFamily() != null ? member.getFamily().getId() : null)
                        .year(currentMonth.getYear())
                        .month(currentMonth.getMonthValue())
                        .category(CATEGORY_OVERALL)
                        .insightType(INSIGHT_TYPE_SUMMARY)
                        .build());

        insight.setBaselineAmount(overallBaseline);
        insight.setCurrentAmount(overallCurrent);
        insight.setDifferenceAmount(result.differenceAmount());
        insight.setIncreasePercentage(result.increasePercentage());
        insight.setSeverity(trend.state().name());
        insight.setConsecutiveElevatedMonths(trend.consecutiveElevatedMonths());
        insight.setTopCategoryIncreases(serializeIncreases(increased));
        insight.setNewRecurringExpenseAmount(newRecurring != null ? newRecurring.amount() : null);
        insight.setNewRecurringExpenseLabel(newRecurring != null ? newRecurring.label() : null);
        insight.setIncomeGrowthPercent(incomeGrowthPercent);
        insight.setStatus(STATUS_ACTIVE);
        insight.setMessage(message);
        insightRepository.save(insight);
    }

    private String serializeIncreases(List<CategoryFigures> increased) {
        if (increased.isEmpty()) {
            return null;
        }
        return increased.stream()
                .map(f -> f.category() + ":" + f.difference().setScale(2, RoundingMode.HALF_UP))
                .collect(Collectors.joining(","));
    }

    private BigDecimal amountFor(Member member, YearMonth month, String category) {
        return summaryRepository.findByMemberIdAndYearAndMonthAndCategory(member.getId(), month.getYear(), month.getMonthValue(), category)
                .map(MemberMonthlySummary::getTotalAmount)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal prorate(BigDecimal baselineAmount, boolean isMonthToDate, int dayOfMonth, int daysInMonth) {
        if (!isMonthToDate || baselineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return baselineAmount;
        }
        return baselineAmount.multiply(BigDecimal.valueOf(dayOfMonth)).divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(java.util.stream.Stream<BigDecimal> amounts) {
        return amounts.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String ordinal(int n) {
        return switch (n) {
            case 1 -> "first";
            case 2 -> "second";
            case 3 -> "third";
            default -> n + "th";
        };
    }

    private String formatInr(BigDecimal amount) {
        return "₹" + NumberFormat.getIntegerInstance(INDIA).format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
