package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.entity.Goal;
import com.prospr.app.entity.LifestyleBaseline;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySummary;
import com.prospr.app.repository.GoalRepository;
import com.prospr.app.repository.LifestyleBaselineRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;

@ExtendWith(MockitoExtension.class)
class ActionableInsightServiceTest {

    private static final YearMonth MONTH = YearMonth.of(2026, 8);
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);

    @Mock
    private LifestyleBaselineRepository baselineRepository;
    @Mock
    private MemberMonthlySummaryRepository summaryRepository;
    @Mock
    private GoalRepository goalRepository;

    private final LifestyleCreepAnalyzer analyzer = new LifestyleCreepAnalyzer();
    private final LifestyleCategoryCatalog catalog = new LifestyleCategoryCatalog();
    private final LifestyleProperties properties = new LifestyleProperties();
    private final LifestyleTrendEvaluator trendEvaluator = new LifestyleTrendEvaluator(properties);

    private ActionableInsightService service() {
        return new ActionableInsightService(baselineRepository, summaryRepository, goalRepository,
                analyzer, trendEvaluator, catalog, properties);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private LifestyleBaseline baseline(String category, String amount) {
        return LifestyleBaseline.builder().category(category).baselineAmount(new BigDecimal(amount)).build();
    }

    private void stubCurrent(Member member, String category, String amount) {
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(
                member.getId(), MONTH.getYear(), MONTH.getMonthValue(), category))
                .thenReturn(Optional.of(MemberMonthlySummary.builder().totalAmount(new BigDecimal(amount)).build()));
    }

    private Goal goal(String name, String target, String current, String monthlyContribution, LocalDate targetDate) {
        return Goal.builder().id(UUID.randomUUID()).goalName(name).goalType(GoalType.MARRIAGE)
                .targetAmount(new BigDecimal(target)).currentAmount(new BigDecimal(current))
                .monthlyContribution(new BigDecimal(monthlyContribution)).targetDate(targetDate).build();
    }

    @Test
    void reduceInsightSuggestsATwentyPercentCutOnTheHighestMeaningfulCategoryAndTiesItToAGoal() {
        Member member = member();
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(List.of(baseline("DINING", "4000")));
        stubCurrent(member, "DINING", "4000");
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(
                List.of(goal("Marriage", "1000000", "250000", "30000", TODAY.plusMonths(28))));

        List<ActionableInsightService.ActionableInsight> insights = service().buildInsights(member, MONTH, TODAY, TODAY);

        assertThat(insights).hasSize(1);
        ActionableInsightService.ActionableInsight insight = insights.get(0);
        assertThat(insight.type()).isEqualTo(ActionableInsightService.TYPE_REDUCE);
        assertThat(insight.monthlyImpact()).isEqualByComparingTo("800");
        assertThat(insight.message()).contains("Dining").contains("800").contains("9,600").contains("Marriage goal");
    }

    @Test
    void returnToNormalInsightOnlyFiresWhenTheIncreaseClearsTheExistingLifestyleSignificanceGate() {
        Member member = member();
        // +10% / +200 - below the 15%/₹500 "elevated" gate reused from LifestyleTrendEvaluator.
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(List.of(baseline("SHOPPING", "2000")));
        stubCurrent(member, "SHOPPING", "2200");
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());

        List<ActionableInsightService.ActionableInsight> insights = service().buildInsights(member, MONTH, TODAY, TODAY);

        assertThat(insights).extracting(ActionableInsightService.ActionableInsight::type)
                .doesNotContain(ActionableInsightService.TYPE_RETURN_TO_NORMAL);
    }

    @Test
    void returnToNormalInsightFiresOnceTheGateIsCleared() {
        Member member = member();
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(List.of(baseline("SHOPPING", "2000")));
        stubCurrent(member, "SHOPPING", "5200");
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());

        List<ActionableInsightService.ActionableInsight> insights = service().buildInsights(member, MONTH, TODAY, TODAY);

        assertThat(insights).hasSize(1);
        ActionableInsightService.ActionableInsight insight = insights.get(0);
        assertThat(insight.type()).isEqualTo(ActionableInsightService.TYPE_RETURN_TO_NORMAL);
        assertThat(insight.monthlyImpact()).isEqualByComparingTo("3200");
        assertThat(insight.message()).contains("Shopping").contains("3,200");
    }

    @Test
    void goalOpportunityInsightFiresForAGoalBehindItsRequiredContribution() {
        Member member = member();
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(List.of());
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(
                List.of(goal("Marriage", "1000000", "250000", "20000", TODAY.plusMonths(28))));

        List<ActionableInsightService.ActionableInsight> insights = service().buildInsights(member, MONTH, TODAY, TODAY);

        assertThat(insights).hasSize(1);
        ActionableInsightService.ActionableInsight insight = insights.get(0);
        assertThat(insight.type()).isEqualTo(ActionableInsightService.TYPE_GOAL_OPPORTUNITY);
        assertThat(insight.message()).contains("Marriage").contains("26,786");
    }

    @Test
    void aCategoryClaimedByReturnToNormalIsNeverAlsoOfferedAsAReduceCandidate() {
        Member member = member();
        // SHOPPING is both the single meaningful-spend category AND significantly above baseline -
        // it must only surface once, as RETURN_TO_NORMAL (the more specific, evidence-driven signal).
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(List.of(baseline("SHOPPING", "2000")));
        stubCurrent(member, "SHOPPING", "5200");
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());

        List<ActionableInsightService.ActionableInsight> insights = service().buildInsights(member, MONTH, TODAY, TODAY);

        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).type()).isEqualTo(ActionableInsightService.TYPE_RETURN_TO_NORMAL);
    }

    @Test
    void returnsNoInsightsWhenThereIsNoQualifyingEvidence() {
        Member member = member();
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(List.of(baseline("DINING", "2000")));
        stubCurrent(member, "DINING", "100");
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(
                List.of(goal("Marriage", "1000000", "1000000", "0", TODAY.plusMonths(12))));

        List<ActionableInsightService.ActionableInsight> insights = service().buildInsights(member, MONTH, TODAY, TODAY);

        assertThat(insights).isEmpty();
    }

    @Test
    void neverReturnsMoreThanThreeInsights() {
        Member member = member();
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(List.of(
                baseline("DINING", "4000"), baseline("SHOPPING", "2000")));
        stubCurrent(member, "DINING", "4000");
        stubCurrent(member, "SHOPPING", "5200");
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(
                List.of(goal("Marriage", "1000000", "250000", "20000", TODAY.plusMonths(28))));

        List<ActionableInsightService.ActionableInsight> insights = service().buildInsights(member, MONTH, TODAY, TODAY);

        assertThat(insights).hasSizeLessThanOrEqualTo(3);
        assertThat(insights).extracting(ActionableInsightService.ActionableInsight::type)
                .containsExactlyInAnyOrder(ActionableInsightService.TYPE_RETURN_TO_NORMAL,
                        ActionableInsightService.TYPE_REDUCE, ActionableInsightService.TYPE_GOAL_OPPORTUNITY);
    }
}
