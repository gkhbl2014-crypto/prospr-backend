package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.prospr.app.dto.response.MonthlySnapshotResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;
import com.prospr.app.repository.MemberMonthlySummaryRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.CategoryTaxonomy;
import com.prospr.app.service.FinancialAnalysisOrchestratorService;

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
    private Authentication authentication;

    private final CategoryTaxonomy categoryTaxonomy = new CategoryTaxonomy();

    private FinancialSummaryController controller() {
        return new FinancialSummaryController(snapshotRepository, summaryRepository, memberRepository,
                financialAnalysisOrchestratorService, categoryTaxonomy);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").build();
    }

    private void stubCaller(Member member) {
        when(authentication.getName()).thenReturn(member.getEmail());
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    private MemberMonthlySnapshot snapshot(Member member, int year, int month, String income, String expenses, String rate) {
        return MemberMonthlySnapshot.builder().member(member).year(year).month(month)
                .totalIncome(new BigDecimal(income)).totalEssential(BigDecimal.ZERO)
                .totalDiscretionary(BigDecimal.ZERO).totalInvestment(BigDecimal.ZERO)
                .totalDebtRepayment(BigDecimal.ZERO).totalInsurance(BigDecimal.ZERO)
                .totalInternalTransfer(BigDecimal.ZERO).totalCashWithdrawal(BigDecimal.ZERO)
                .totalUnknown(BigDecimal.ZERO).totalExpenses(new BigDecimal(expenses))
                .savings(new BigDecimal(income).subtract(new BigDecimal(expenses)))
                .savingsRate(rate == null ? null : new BigDecimal(rate))
                .transactionCount(5).hasData(true).build();
    }

    @Test
    void returnsMonthsInAscendingOrderWithMonthOverMonthDeltas() {
        Member member = member();
        stubCaller(member);
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
}
