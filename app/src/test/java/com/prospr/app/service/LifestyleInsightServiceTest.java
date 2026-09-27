package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * baselineMonths is set to 2 (not the real default of 6) purely to keep the number of
 * MemberMonthlySummary/MemberMonthlySnapshot months that need stubbing small - the trend lookback,
 * message wording, and income-comparison logic are all baselineMonths-agnostic.
 */
@ExtendWith(MockitoExtension.class)
class LifestyleInsightServiceTest {

    private static final String DINING = "DINING";

    @Mock
    private MemberMonthlySummaryRepository summaryRepository;
    @Mock
    private LifestyleBaselineRepository baselineRepository;
    @Mock
    private LifestyleInsightRepository insightRepository;
    @Mock
    private MemberMonthlySnapshotRepository snapshotRepository;
    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    private final LifestyleProperties properties = new LifestyleProperties();
    private final LifestyleCategoryCatalog catalog = new LifestyleCategoryCatalog();
    private final LifestyleCreepAnalyzer analyzer = new LifestyleCreepAnalyzer();
    private LifestyleTrendEvaluator trendEvaluator;

    private LifestyleInsightService service() {
        properties.setBaselineMonths(2);
        trendEvaluator = new LifestyleTrendEvaluator(properties);
        return new LifestyleInsightService(summaryRepository, baselineRepository, insightRepository,
                snapshotRepository, recurringExpenseRepository, analyzer, trendEvaluator, catalog, properties);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private LifestyleBaseline diningBaseline(Member member, String amount) {
        return LifestyleBaseline.builder().member(member).category(DINING).baselineAmount(new BigDecimal(amount)).build();
    }

    private void stubCurrentMonthAmount(Member member, YearMonth month, String amount) {
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(
                eq(member.getId()), eq(month.getYear()), eq(month.getMonthValue()), eq(DINING)))
                .thenReturn(Optional.of(MemberMonthlySummary.builder().totalAmount(new BigDecimal(amount)).build()));
    }

    private LifestyleInsight recompute(Member member, YearMonth currentMonth, List<LifestyleBaseline> baselines) {
        when(baselineRepository.findByMemberId(member.getId())).thenReturn(baselines);
        when(insightRepository.findByMemberIdAndYearAndMonthAndCategoryAndInsightType(
                eq(member.getId()), eq(currentMonth.getYear()), eq(currentMonth.getMonthValue()),
                eq(LifestyleInsightService.CATEGORY_OVERALL), eq(LifestyleInsightService.INSIGHT_TYPE_SUMMARY)))
                .thenReturn(Optional.empty());

        // Last day of the month, so month-to-date proration never kicks in and the raw baseline
        // applies directly - keeps the arithmetic in these tests exact.
        service().recomputeForMember(member, currentMonth, currentMonth.atEndOfMonth());

        ArgumentCaptor<LifestyleInsight> captor = ArgumentCaptor.forClass(LifestyleInsight.class);
        Mockito.verify(insightRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void stableSpendingProducesARealSummaryWithActualNumbersNotAGenericEmptyMessage() {
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        stubCurrentMonthAmount(member, currentMonth, "4000");

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getSeverity()).isEqualTo("STABLE");
        assertThat(result.getConsecutiveElevatedMonths()).isZero();
        assertThat(result.getCurrentAmount()).isEqualByComparingTo("4000");
        assertThat(result.getBaselineAmount()).isEqualByComparingTo("4000");
        assertThat(result.getMessage()).contains("Your discretionary spending is stable.");
        assertThat(result.getMessage()).contains("You spent ₹4,000 this month compared with your 2-month average of ₹4,000.");
        assertThat(result.getMessage()).contains("No significant lifestyle creep detected.");
    }

    @Test
    void firstMonthOfElevatedSpendingIsWatchNotIncreasing() {
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        stubCurrentMonthAmount(member, currentMonth, "6000");
        // Previous month deliberately left unstubbed -> defaults to zero -> not elevated.

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getSeverity()).isEqualTo("WATCH");
        assertThat(result.getConsecutiveElevatedMonths()).isEqualTo(1);
        assertThat(result.getMessage()).contains("Your lifestyle spending is starting to rise.");
        assertThat(result.getMessage()).contains("The biggest increases were:");
        assertThat(result.getMessage()).contains("Dining: +₹2,000");
        assertThat(result.getMessage()).contains("This is the first month of increased discretionary spending");
    }

    @Test
    void twoConsecutiveElevatedMonthsIsIncreasingNotWatch() {
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        YearMonth previousMonth = currentMonth.minusMonths(1);
        stubCurrentMonthAmount(member, currentMonth, "6000");
        when(summaryRepository.findByMemberIdAndYearAndMonthAndCategory(
                eq(member.getId()), eq(previousMonth.getYear()), eq(previousMonth.getMonthValue()), eq(DINING)))
                .thenReturn(Optional.of(MemberMonthlySummary.builder().totalAmount(new BigDecimal("6000")).build()));

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getSeverity()).isEqualTo("INCREASING");
        assertThat(result.getConsecutiveElevatedMonths()).isEqualTo(2);
        assertThat(result.getMessage()).contains("Your lifestyle spending has been rising for 2 months in a row.");
        assertThat(result.getMessage()).contains("This is the second month of increased discretionary spending");
        assertThat(result.getMessage()).contains("sustained pattern");
    }

    @Test
    void aQualifyingNewRecurringExpenseIsCalledOutByName() {
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        stubCurrentMonthAmount(member, currentMonth, "6000");
        RecurringExpense netflix = RecurringExpense.builder()
                .merchantKey("netflix").category(DINING).status("ACTIVE")
                .typicalAmount(new BigDecimal("649")).firstSeenDate(LocalDate.of(2026, 7, 15)).build();
        when(recurringExpenseRepository.findByMemberIdOrderByTypicalAmountDesc(member.getId())).thenReturn(List.of(netflix));

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getNewRecurringExpenseLabel()).isEqualTo("Netflix");
        assertThat(result.getNewRecurringExpenseAmount()).isEqualByComparingTo("649");
        assertThat(result.getMessage()).contains("₹649 of this comes from a new recurring expense (Netflix)");
    }

    @Test
    void aRecurringExpenseSeenBeforeTheBaselineWindowIsNotTreatedAsNew() {
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        stubCurrentMonthAmount(member, currentMonth, "6000");
        RecurringExpense oldGym = RecurringExpense.builder()
                .merchantKey("cultfit").category(DINING).status("ACTIVE")
                .typicalAmount(new BigDecimal("1500")).firstSeenDate(LocalDate.of(2025, 1, 1)).build();
        when(recurringExpenseRepository.findByMemberIdOrderByTypicalAmountDesc(member.getId())).thenReturn(List.of(oldGym));

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getNewRecurringExpenseLabel()).isNull();
        assertThat(result.getMessage()).doesNotContain("new recurring expense");
    }

    @Test
    void incomeComparisonAppearsOnlyWhenReliableForEveryRelevantMonth() {
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        stubCurrentMonthAmount(member, currentMonth, "6000");
        stubIncome(member, currentMonth, "105000");
        stubIncome(member, currentMonth.minusMonths(1), "100000");
        stubIncome(member, currentMonth.minusMonths(2), "100000");

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getIncomeGrowthPercent()).isNotNull();
        assertThat(result.getMessage()).contains("Your income grew");
    }

    @Test
    void incomeComparisonIsOmittedEntirelyWhenAnyRelevantMonthIsUnreliable() {
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        stubCurrentMonthAmount(member, currentMonth, "6000");
        stubIncome(member, currentMonth, "105000");
        stubIncome(member, currentMonth.minusMonths(2), "100000");
        // No snapshot for this one baseline month -> unreliable -> the whole comparison must be
        // omitted, even though every other month involved is perfectly fine.
        when(snapshotRepository.findByMemberIdAndYearAndMonth(member.getId(),
                currentMonth.minusMonths(1).getYear(), currentMonth.minusMonths(1).getMonthValue()))
                .thenReturn(Optional.empty());

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getIncomeGrowthPercent()).isNull();
        assertThat(result.getMessage()).doesNotContain("income");
    }

    @Test
    void neverAppliesTheStrictOldInsightWorthyGateJustTheSimplerElevatedCheck() {
        // 20% increase / Rs800 difference clears the new 15%+Rs500 bar but would NOT have cleared the
        // old design's 30%+Rs500 double-gate - confirms the redesign actually surfaces more, not less.
        Member member = member();
        YearMonth currentMonth = YearMonth.of(2026, 8);
        stubCurrentMonthAmount(member, currentMonth, "4800");

        LifestyleInsight result = recompute(member, currentMonth, List.of(diningBaseline(member, "4000")));

        assertThat(result.getSeverity()).isEqualTo("WATCH");
    }

    private void stubIncome(Member member, YearMonth month, String totalIncome) {
        when(snapshotRepository.findByMemberIdAndYearAndMonth(member.getId(), month.getYear(), month.getMonthValue()))
                .thenReturn(Optional.of(MemberMonthlySnapshot.builder().totalIncome(new BigDecimal(totalIncome)).build()));
    }
}
