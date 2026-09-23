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
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.config.FinancialAnalysisProperties;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;
import com.prospr.app.repository.TransactionRepository;
import com.prospr.app.service.cache.AnalyticsCacheService;

@ExtendWith(MockitoExtension.class)
class MonthlySnapshotServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MemberMonthlySnapshotRepository snapshotRepository;
    @Mock
    private AnalyticsCacheService analyticsCacheService;

    private final FinancialAnalysisProperties properties = new FinancialAnalysisProperties();
    private final EssentialCategoryCatalog essentialCategoryCatalog = new EssentialCategoryCatalog();

    private MonthlySnapshotService service() {
        return new MonthlySnapshotService(transactionRepository, snapshotRepository, properties,
                analyticsCacheService, essentialCategoryCatalog);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private void stubNoExistingSnapshots(Member member) {
        when(snapshotRepository.findByMemberIdAndYearAndMonth(eq(member.getId()), any(), any()))
                .thenReturn(Optional.empty());
    }

    private MemberMonthlySnapshot recomputeCurrentMonth(Member member, List<Transaction> history) {
        stubNoExistingSnapshots(member);
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        YearMonth current = YearMonth.now();
        ArgumentCaptor<MemberMonthlySnapshot> captor = ArgumentCaptor.forClass(MemberMonthlySnapshot.class);
        org.mockito.Mockito.verify(snapshotRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream()
                .filter(s -> s.getYear().equals(current.getYear()) && s.getMonth().equals(current.getMonthValue()))
                .findFirst().orElseThrow();
    }

    @Test
    void computesIncomeExpenseAndSavingsRateForACompleteMonth() {
        Member member = member();
        LocalDate today = LocalDate.now();
        List<Transaction> history = List.of(
                income(today, new BigDecimal("100000")),
                essential(today, "RENT", new BigDecimal("30000")),
                discretionary(today, "SHOPPING", new BigDecimal("20000")));

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getTotalIncome()).isEqualByComparingTo("100000");
        assertThat(snapshot.getTotalExpenses()).isEqualByComparingTo("50000");
        assertThat(snapshot.getSavings()).isEqualByComparingTo("50000");
        assertThat(snapshot.getSavingsRate()).isEqualByComparingTo("50.00");
        assertThat(snapshot.getHasData()).isTrue();
        assertThat(snapshot.getReconciled()).isTrue();
    }

    @Test
    void zeroIncomeMonthHasNullSavingsRateNotZero() {
        Member member = member();
        LocalDate today = LocalDate.now();
        List<Transaction> history = List.of(essential(today, "RENT", new BigDecimal("5000")));

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getSavingsRate()).isNull();
        assertThat(snapshot.getSavings()).isEqualByComparingTo("-5000");
    }

    @Test
    void monthWithNoTransactionsAtAllHasNoDataAndNullSavings() {
        Member member = member();

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, List.of());

        assertThat(snapshot.getHasData()).isFalse();
        assertThat(snapshot.getSavings()).isNull();
        assertThat(snapshot.getTransactionCount()).isZero();
    }

    @Test
    void hiddenTransactionsAreStillCountedInTotals() {
        Member member = member();
        LocalDate today = LocalDate.now();
        List<Transaction> history = List.of(
                income(today, new BigDecimal("50000")),
                Transaction.builder().type("DEBIT").transactionType("EXPENSE").category("SHOPPING")
                        .valueDate(today).amount(new BigDecimal("10000")).isHidden(true).build());

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getTotalDiscretionary()).isEqualByComparingTo("10000");
        assertThat(snapshot.getTotalExpenses()).isEqualByComparingTo("10000");
    }

    @Test
    void internalTransferIsExcludedFromTotalExpenses() {
        Member member = member();
        LocalDate today = LocalDate.now();
        List<Transaction> history = List.of(
                income(today, new BigDecimal("50000")),
                Transaction.builder().type("DEBIT").transactionType("INTERNAL_TRANSFER")
                        .valueDate(today).amount(new BigDecimal("20000")).isHidden(false).build(),
                essential(today, "RENT", new BigDecimal("5000")));

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getTotalInternalTransfer()).isEqualByComparingTo("20000");
        assertThat(snapshot.getTotalExpenses()).isEqualByComparingTo("5000");
    }

    /**
     * The exact worked example from the financial-analysis-audit spec: ₹30,000 salary, ₹5,000 food +
     * ₹2,000 shopping (both discretionary/lifestyle categories, so EXPENSE), a ₹10,000 own-account
     * transfer pair, a ₹3,000 credit-card payment (both classify to INTERNAL_TRANSFER), and a
     * ₹60,000 investment. Verifies totalCashOutflow, and that the two independently-computed
     * reconciliation anchors (raw type=CREDIT/DEBIT sums vs. classified bucket sums) agree exactly.
     */
    @Test
    void reconciliationBalancesForTheWorkedExampleFromTheAuditSpec() {
        Member member = member();
        LocalDate today = LocalDate.now();
        List<Transaction> history = List.of(
                income(today, new BigDecimal("30000")),
                discretionary(today, "DINING", new BigDecimal("5000")),
                discretionary(today, "SHOPPING", new BigDecimal("2000")),
                Transaction.builder().type("DEBIT").transactionType("INTERNAL_TRANSFER")
                        .valueDate(today).amount(new BigDecimal("10000")).isHidden(false).build(),
                Transaction.builder().type("CREDIT").transactionType("INTERNAL_TRANSFER")
                        .valueDate(today).amount(new BigDecimal("10000")).isHidden(false).build(),
                Transaction.builder().type("DEBIT").transactionType("INTERNAL_TRANSFER").category("CREDIT_CARD_PAYMENT")
                        .valueDate(today).amount(new BigDecimal("3000")).isHidden(false).build(),
                Transaction.builder().type("DEBIT").transactionType("INVESTMENT").category("INVESTMENT")
                        .valueDate(today).amount(new BigDecimal("60000")).isHidden(false).build());

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getTotalIncome()).isEqualByComparingTo("30000");
        assertThat(snapshot.getTotalExpenses()).isEqualByComparingTo("7000");
        assertThat(snapshot.getTotalInvestment()).isEqualByComparingTo("60000");
        assertThat(snapshot.getTotalInternalTransfer()).isEqualByComparingTo("23000");
        assertThat(snapshot.getTotalCashOutflow()).isEqualByComparingTo("67000");
        assertThat(snapshot.getSavings()).isEqualByComparingTo("-37000");
        assertThat(snapshot.getSavingsRate()).isEqualByComparingTo("-123.33");
        assertThat(snapshot.getReconciled()).isTrue();
        assertThat(snapshot.getDifference()).isEqualByComparingTo("0");
    }

    @Test
    void refundIsExcludedFromIncomeAndOtherCreditNeverSilentlyDisappears() {
        Member member = member();
        LocalDate today = LocalDate.now();
        List<Transaction> history = List.of(
                income(today, new BigDecimal("30000")),
                Transaction.builder().type("CREDIT").transactionType("REFUND").category("REFUND")
                        .valueDate(today).amount(new BigDecimal("500")).isHidden(false).build(),
                Transaction.builder().type("CREDIT").transactionType("OTHER_CREDIT")
                        .valueDate(today).amount(new BigDecimal("200")).isHidden(false).build());

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getTotalIncome()).isEqualByComparingTo("30000");
        assertThat(snapshot.getTotalRefund()).isEqualByComparingTo("500");
        assertThat(snapshot.getTotalOtherCredit()).isEqualByComparingTo("200");
        assertThat(snapshot.getReconciled()).isTrue();
    }

    private Transaction income(LocalDate date, BigDecimal amount) {
        return Transaction.builder().type("CREDIT").transactionType("INCOME").category("SALARY_INCOME")
                .valueDate(date).amount(amount).isHidden(false).build();
    }

    private Transaction essential(LocalDate date, String category, BigDecimal amount) {
        return Transaction.builder().type("DEBIT").transactionType("EXPENSE").category(category)
                .valueDate(date).amount(amount).isHidden(false).build();
    }

    private Transaction discretionary(LocalDate date, String category, BigDecimal amount) {
        return Transaction.builder().type("DEBIT").transactionType("EXPENSE").category(category)
                .valueDate(date).amount(amount).isHidden(false).build();
    }
}
