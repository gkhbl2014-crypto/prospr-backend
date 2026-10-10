package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.entity.Goal;
import com.prospr.app.entity.LifestyleBaseline;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.repository.GoalRepository;
import com.prospr.app.repository.LifestyleBaselineRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;

/**
 * "What could I do about it" layer on top of data other services already compute - deliberately NOT a
 * new analysis engine. Every number here reuses {@link LifestyleCreepAnalyzer}/{@link
 * LifestyleTrendEvaluator} (is this category meaningfully above its {@link LifestyleBaseline}?) and
 * {@link GoalCalculator} (is this {@link Goal} behind its required contribution?); this class only
 * decides which of the resulting facts are worth surfacing together and phrases them as a suggestion
 * plus its concrete rupee impact. No forecasting, no scoring model, no AI - just arithmetic on numbers
 * those other services already own.
 */
@Service
public class ActionableInsightService {

    public static final String TYPE_REDUCE = "REDUCE";
    public static final String TYPE_RETURN_TO_NORMAL = "RETURN_TO_NORMAL";
    public static final String TYPE_GOAL_OPPORTUNITY = "GOAL_OPPORTUNITY";

    private static final BigDecimal REDUCTION_RATE = new BigDecimal("0.20");
    private static final int MAX_INSIGHTS = 3;
    private static final Locale INDIA = Locale.of("en", "IN");

    public record ActionableInsight(String type, String label, String message, BigDecimal monthlyImpact) {
    }

    private record Candidate(String category, ActionableInsight insight) {
    }

    private record CategoryFigures(String category, BigDecimal currentAmount,
                                    LifestyleCreepAnalyzer.AnalysisResult analysis) {
    }

    private record GoalWithProgress(Goal goal, GoalCalculator.Progress progress) {
    }

    private final LifestyleBaselineRepository baselineRepository;
    private final MemberMonthlySummaryRepository summaryRepository;
    private final GoalRepository goalRepository;
    private final LifestyleCreepAnalyzer analyzer;
    private final LifestyleTrendEvaluator trendEvaluator;
    private final LifestyleCategoryCatalog catalog;
    private final LifestyleProperties properties;

    public ActionableInsightService(LifestyleBaselineRepository baselineRepository,
                                     MemberMonthlySummaryRepository summaryRepository,
                                     GoalRepository goalRepository,
                                     LifestyleCreepAnalyzer analyzer,
                                     LifestyleTrendEvaluator trendEvaluator,
                                     LifestyleCategoryCatalog catalog,
                                     LifestyleProperties properties) {
        this.baselineRepository = baselineRepository;
        this.summaryRepository = summaryRepository;
        this.goalRepository = goalRepository;
        this.analyzer = analyzer;
        this.trendEvaluator = trendEvaluator;
        this.catalog = catalog;
        this.properties = properties;
    }

    /**
     * @param month             the month to evaluate category spend for - the member's latest DATA
     *                          month (see {@link CurrentMonthResolver}), not necessarily today's
     *                          wall-clock month.
     * @param categoryAsOfDate  the date to treat as "today" when deciding whether {@code month} is
     *                          still partial (for baseline prorating) - see {@link
     *                          CurrentMonthResolver.EffectivePeriod}.
     * @param today             the REAL wall-clock date, used only for goal deadline math ({@link
     *                          GoalCalculator}), which is about real-world target dates and must never
     *                          be backdated just because imported transaction data lags behind.
     */
    public List<ActionableInsight> buildInsights(Member member, YearMonth month, LocalDate categoryAsOfDate, LocalDate today) {
        List<CategoryFigures> figures = loadCategoryFigures(member, month, categoryAsOfDate);
        List<GoalWithProgress> goalsWithProgress = goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .map(goal -> new GoalWithProgress(goal,
                        GoalCalculator.evaluate(goal.getTargetAmount(), goal.getCurrentAmount(),
                                goal.getMonthlyContribution(), goal.getTargetDate(), today)))
                .toList();

        List<Candidate> candidates = new ArrayList<>();
        Set<String> usedCategories = new HashSet<>();

        findReturnToNormalCandidate(figures).ifPresent(c -> {
            candidates.add(c);
            usedCategories.add(c.category());
        });

        GoalWithProgress referenceGoal = pickReferenceGoal(goalsWithProgress);
        findReduceCandidate(figures, usedCategories, referenceGoal).ifPresent(candidates::add);

        findGoalOpportunityCandidate(goalsWithProgress).ifPresent(candidates::add);

        return candidates.stream()
                .map(Candidate::insight)
                .sorted(Comparator.comparing(ActionableInsight::monthlyImpact).reversed())
                .limit(MAX_INSIGHTS)
                .toList();
    }

    /** The goal a REDUCE insight's savings are measured against: whichever goal is furthest behind its
     *  required contribution, or - if none are behind - the most recently created goal still in
     *  progress. Null (and REDUCE simply omits the goal-impact sentence) when every goal is completed
     *  or the member has none. */
    private GoalWithProgress pickReferenceGoal(List<GoalWithProgress> goalsWithProgress) {
        return goalsWithProgress.stream()
                .filter(g -> GoalStatus.NEEDS_ATTENTION.equals(g.progress().status()))
                .max(Comparator.comparing(g -> gap(g.goal(), g.progress())))
                .or(() -> goalsWithProgress.stream()
                        .filter(g -> GoalStatus.ON_TRACK.equals(g.progress().status()))
                        .findFirst())
                .orElse(null);
    }

    private Optional<Candidate> findReturnToNormalCandidate(List<CategoryFigures> figures) {
        return figures.stream()
                .filter(f -> trendEvaluator.isElevated(f.analysis()))
                .max(Comparator.comparing(f -> f.analysis().differenceAmount()))
                .map(f -> {
                    BigDecimal diff = f.analysis().differenceAmount();
                    String label = catalog.label(f.category());
                    String message = label + " is " + formatInr(diff) + " above your usual monthly spending. "
                            + "Returning to your normal level could free up " + formatInr(diff) + " this month.";
                    return new Candidate(f.category(), new ActionableInsight(TYPE_RETURN_TO_NORMAL, label, message, diff));
                });
    }

    private Optional<Candidate> findReduceCandidate(List<CategoryFigures> figures, Set<String> excludedCategories,
                                                     GoalWithProgress referenceGoal) {
        BigDecimal meaningfulFloor = BigDecimal.valueOf(properties.getMinAbsoluteDifferenceInr());
        return figures.stream()
                .filter(f -> !excludedCategories.contains(f.category()))
                .filter(f -> f.currentAmount().compareTo(meaningfulFloor) >= 0)
                .max(Comparator.comparing(CategoryFigures::currentAmount))
                .map(f -> {
                    String label = catalog.label(f.category());
                    BigDecimal monthlySavings = f.currentAmount().multiply(REDUCTION_RATE).setScale(0, RoundingMode.HALF_UP);
                    BigDecimal yearlySavings = monthlySavings.multiply(BigDecimal.valueOf(12));

                    StringBuilder message = new StringBuilder("You spent ").append(formatInr(f.currentAmount()))
                            .append(" on ").append(label).append(" this month. Reducing ").append(label)
                            .append(" by 20% could save ").append(formatInr(monthlySavings)).append("/month or ")
                            .append(formatInr(yearlySavings)).append("/year.");
                    appendGoalImpact(message, referenceGoal, yearlySavings);

                    return new Candidate(f.category(), new ActionableInsight(TYPE_REDUCE, label, message.toString(), monthlySavings));
                });
    }

    private void appendGoalImpact(StringBuilder message, GoalWithProgress referenceGoal, BigDecimal yearlySavings) {
        if (referenceGoal == null) {
            return;
        }
        BigDecimal remaining = referenceGoal.progress().remainingAmount();
        if (remaining.signum() <= 0) {
            return;
        }
        double impactPercent = yearlySavings.divide(remaining, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).doubleValue();
        message.append(" That could add ").append(round1(impactPercent)).append("% toward your ")
                .append(formatInr(referenceGoal.goal().getTargetAmount())).append(' ')
                .append(referenceGoal.goal().getGoalName()).append(" goal.");
    }

    private Optional<Candidate> findGoalOpportunityCandidate(List<GoalWithProgress> goalsWithProgress) {
        return goalsWithProgress.stream()
                .filter(g -> GoalStatus.NEEDS_ATTENTION.equals(g.progress().status()))
                .filter(g -> gap(g.goal(), g.progress()).signum() > 0)
                .max(Comparator.comparing(g -> gap(g.goal(), g.progress())))
                .map(g -> {
                    BigDecimal gap = gap(g.goal(), g.progress());
                    BigDecimal required = g.progress().requiredMonthlyContribution();
                    String message = "You need " + formatInr(required) + "/month for your " + g.goal().getGoalName()
                            + " goal. Saving an additional " + formatInr(gap) + "/month would help close the gap.";
                    return new Candidate("GOAL:" + g.goal().getId(),
                            new ActionableInsight(TYPE_GOAL_OPPORTUNITY, g.goal().getGoalName(), message, gap));
                });
    }

    private BigDecimal gap(Goal goal, GoalCalculator.Progress progress) {
        return progress.requiredMonthlyContribution().subtract(goal.getMonthlyContribution()).max(BigDecimal.ZERO);
    }

    private List<CategoryFigures> loadCategoryFigures(Member member, YearMonth month, LocalDate today) {
        int dayOfMonth = today.getDayOfMonth();
        int daysInMonth = today.lengthOfMonth();
        boolean isMonthToDate = YearMonth.from(today).equals(month) && dayOfMonth < daysInMonth;

        List<LifestyleBaseline> baselines = baselineRepository.findByMemberId(member.getId()).stream()
                .filter(b -> catalog.isLifestyleCategory(b.getCategory()))
                .toList();

        List<CategoryFigures> figures = new ArrayList<>();
        for (LifestyleBaseline baseline : baselines) {
            BigDecimal currentAmount = summaryRepository
                    .findByMemberIdAndYearAndMonthAndCategory(member.getId(), month.getYear(), month.getMonthValue(), baseline.getCategory())
                    .map(MemberMonthlySummary::getTotalAmount)
                    .orElse(BigDecimal.ZERO);
            BigDecimal comparisonBaseline = prorate(baseline.getBaselineAmount(), isMonthToDate, dayOfMonth, daysInMonth);
            figures.add(new CategoryFigures(baseline.getCategory(), currentAmount, analyzer.analyze(comparisonBaseline, currentAmount)));
        }
        return figures;
    }

    private BigDecimal prorate(BigDecimal baselineAmount, boolean isMonthToDate, int dayOfMonth, int daysInMonth) {
        if (!isMonthToDate || baselineAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return baselineAmount;
        }
        return baselineAmount.multiply(BigDecimal.valueOf(dayOfMonth)).divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    private String round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatInr(BigDecimal amount) {
        return "₹" + NumberFormat.getIntegerInstance(INDIA).format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
