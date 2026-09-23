package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.prospr.app.dto.response.MonthlyReconciliationResponse;
import com.prospr.app.dto.response.MonthlySnapshotResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.CategoryTaxonomy;
import com.prospr.app.service.FinancialAnalysisOrchestratorService;
import com.prospr.app.service.LifestyleCategoryCatalog;
import com.prospr.app.service.TransactionClassificationService;
import com.prospr.app.service.cache.AnalyticsCacheService;

@ExtendWith(MockitoExtension.class)
class FinancialSummaryControllerTest {

    @Mock
    private MemberMonthlySnapshotRepository snapshotRepository;
    @Mock
    private MemberMonthlySummaryRepository summaryRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private FinancialAnalysisOrchestratorService financialAnalysisOrchestratorService;
    @Mock
    private AnalyticsCacheService analyticsCacheService;
    @Mock
    private Authentication authentication;

    private final CategoryTaxonomy categoryTaxonomy = new CategoryTaxonomy();
    private final TransactionClassificationService transactionClassificationService =
            new TransactionClassificationService(new LifestyleCategoryCatalog());

    private FinancialSummaryController controller() {
        return new FinancialSummaryController(snapshotRepository, summaryRepository, memberRepository,
                financialAnalysisOrchestratorService, categoryTaxonomy, analyticsCacheService,
                transactionClassificationService);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").build();
    }

    private void stubCaller(Member member) {
        when(authentication.getName()).thenReturn(member.getEmail());
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    /** Simulates an always-empty cache: reads miss, and the stampede guard just runs the loader
     *  inline (single-threaded test, no actual concurrency to guard against). */
    @SuppressWarnings("unchecked")
    private void stubCacheMiss() {
        when(analyticsCacheService.getSpendingSummary(any())).thenReturn(Optional.empty());
        when(analyticsCacheService.withStampedeGuard(anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    private MemberMonthlySnapshot snapshot(Member member, int year, int month, String income, String expenses, String rate) {
        BigDecimal cashOutflow = new BigDecimal(expenses);
        return MemberMonthlySnapshot.builder().member(member).year(year).month(month)
                .totalIncome(new BigDecimal(income)).totalRefund(BigDecimal.ZERO)
                .totalOtherCredit(BigDecimal.ZERO).totalEssential(BigDecimal.ZERO)
                .totalDiscretionary(BigDecimal.ZERO).totalInvestment(BigDecimal.ZERO)
                .totalDebtRepayment(BigDecimal.ZERO).totalInsurance(BigDecimal.ZERO)
                .totalInternalTransfer(BigDecimal.ZERO).totalCashWithdrawal(BigDecimal.ZERO)
                .totalOtherDebit(BigDecimal.ZERO).totalUnknown(BigDecimal.ZERO)
                .totalExpenses(new BigDecimal(expenses)).totalCashOutflow(cashOutflow)
                .savings(new BigDecimal(income).subtract(new BigDecimal(expenses)))
                .savingsRate(rate == null ? null : new BigDecimal(rate))
                .transactionCount(5).hasData(true).reconciled(true).difference(BigDecimal.ZERO).build();
    }

    @Test
    void returnsMonthsInAscendingOrderWithMonthOverMonthDeltas() {
        Member member = member();
        stubCaller(member);
        stubCacheMiss();
        // Repository returns descending (most recent first) - controller must flip to ascending.
        when(snapshotRepository.findByMemberIdOrderByYearDescMonthDesc(member.getId())).thenReturn(List.of(
                snapshot(member, 2026, 8, "60000", "40000", "33.33"),
                snapshot(member, 2026, 7, "50000", "35000", "30.00")));
        when(summaryRepository.findByMemberIdAndYearAndMonth(member.getId(), 2026, 8)).thenReturn(List.of());
        when(summaryRepository.findByMemberIdAndYearAndMonth(member.getId(), 2026, 7)).thenReturn(List.of());

        List<MonthlySnapshotResponse> result = controller().monthly(2, authentication).getBody();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMonth()).isEqualTo(7);
        assertThat(result.get(1).getMonth()).isEqualTo(8);
        assertThat(result.get(0).getIncomeChangeFromPreviousMonth()).isNull();
        assertThat(result.get(1).getIncomeChangeFromPreviousMonth()).isEqualByComparingTo("10000");
        assertThat(result.get(1).getExpensesChangeFromPreviousMonth()).isEqualByComparingTo("5000");
        assertThat(result.get(1).getSavingsRateChangeFromPreviousMonth()).isEqualByComparingTo("3.33");
    }

    @Test
    void defaultsToSixMonthsWhenNoneRequested() {
        Member member = member();
        stubCaller(member);
        stubCacheMiss();
        when(snapshotRepository.findByMemberIdOrderByYearDescMonthDesc(member.getId())).thenReturn(List.of());

        List<MonthlySnapshotResponse> result = controller().monthly(null, authentication).getBody();

        assertThat(result).isEmpty();
    }

    @Test
    void recomputeTriggersOrchestratorForCallerOnly() {
        Member member = member();
        stubCaller(member);

        controller().recompute(authentication);

        verify(financialAnalysisOrchestratorService, times(1)).recomputeForMember(member);
    }

    @Test
    void cacheHitSkipsThePostgresReadEntirely() {
        Member member = member();
        stubCaller(member);
        MonthlySnapshotResponse cachedMonth = MonthlySnapshotResponse.builder()
                .year(2026).month(8).monthLabel("August 2026").build();
        when(analyticsCacheService.getSpendingSummary(member.getId())).thenReturn(Optional.of(List.of(cachedMonth)));

        List<MonthlySnapshotResponse> result = controller().monthly(6, authentication).getBody();

        assertThat(result).containsExactly(cachedMonth);
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void cacheMissComputesFromPostgresAndWritesBackToCache() {
        Member member = member();
        stubCaller(member);
        stubCacheMiss();
        when(snapshotRepository.findByMemberIdOrderByYearDescMonthDesc(member.getId())).thenReturn(List.of(
                snapshot(member, 2026, 8, "60000", "40000", "33.33")));
        when(summaryRepository.findByMemberIdAndYearAndMonth(member.getId(), 2026, 8)).thenReturn(List.of());

        List<MonthlySnapshotResponse> result = controller().monthly(6, authentication).getBody();

        assertThat(result).hasSize(1);
        verify(analyticsCacheService).putSpendingSummary(eq(member.getId()), any());
    }

    @Test
    void reconciliationEndpointReturnsReconciledTrueForANormalMonth() {
        Member member = member();
        stubCaller(member);
        MemberMonthlySnapshot snap = snapshot(member, 2026, 6, "30000", "7000", "-123.33");
        snap.setTotalInvestment(new BigDecimal("60000"));
        snap.setTotalInternalTransfer(new BigDecimal("23000"));
        snap.setTotalCashOutflow(new BigDecimal("67000"));
        when(snapshotRepository.findByMemberIdAndYearAndMonth(member.getId(), 2026, 6))
                .thenReturn(Optional.of(snap));

        MonthlyReconciliationResponse result = controller().reconciliation("2026-06", authentication).getBody();

        assertThat(result.isReconciled()).isTrue();
        assertThat(result.getDifference()).isEqualByComparingTo("0");
        assertThat(result.getIncome()).isEqualByComparingTo("30000");
        assertThat(result.getInvestments()).isEqualByComparingTo("60000");
        assertThat(result.getTotalCashOutflow()).isEqualByComparingTo("67000");
        assertThat(result.getTotalCredits()).isEqualByComparingTo("53000");
        assertThat(result.getTotalDebits()).isEqualByComparingTo("90000");
    }

    @Test
    void reconciliationEndpointThrowsWhenNoSnapshotExistsForTheRequestedMonth() {
        Member member = member();
        stubCaller(member);
        when(snapshotRepository.findByMemberIdAndYearAndMonth(member.getId(), 2026, 6)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                com.prospr.app.exception.ResourceNotFoundException.class,
                () -> controller().reconciliation("2026-06", authentication));
    }
}
