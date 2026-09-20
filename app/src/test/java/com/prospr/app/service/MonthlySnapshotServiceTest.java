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

@ExtendWith(MockitoExtension.class)
class MonthlySnapshotServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private MemberMonthlySnapshotRepository snapshotRepository;

    private final FinancialAnalysisProperties properties = new FinancialAnalysisProperties();

    private MonthlySnapshotService service() {
        return new MonthlySnapshotService(transactionRepository, snapshotRepository, properties);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private Transaction txn(String type, LocalDate date, BigDecimal amount, boolean hidden) {
        return Transaction.builder().type(type).transactionType(type).valueDate(date).amount(amount)
                .isHidden(hidden).build();
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
                essential(today, new BigDecimal("30000")),
                discretionary(today, new BigDecimal("20000")));

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getTotalIncome()).isEqualByComparingTo("100000");
        assertThat(snapshot.getTotalExpenses()).isEqualByComparingTo("50000");
        assertThat(snapshot.getSavings()).isEqualByComparingTo("50000");
        assertThat(snapshot.getSavingsRate()).isEqualByComparingTo("50.00");
        assertThat(snapshot.getHasData()).isTrue();
    }

    @Test
    void zeroIncomeMonthHasNullSavingsRateNotZero() {
        Member member = member();
        LocalDate today = LocalDate.now();
        List<Transaction> history = List.of(essential(today, new BigDecimal("5000")));

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
                Transaction.builder().type("DISCRETIONARY").transactionType("DISCRETIONARY")
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
                essential(today, new BigDecimal("5000")));

        MemberMonthlySnapshot snapshot = recomputeCurrentMonth(member, history);

        assertThat(snapshot.getTotalInternalTransfer()).isEqualByComparingTo("20000");
        assertThat(snapshot.getTotalExpenses()).isEqualByComparingTo("5000");
    }

    private Transaction income(LocalDate date, BigDecimal amount) {
        return Transaction.builder().type("CREDIT").transactionType("INCOME").valueDate(date)
                .amount(amount).isHidden(false).build();
    }

    private Transaction essential(LocalDate date, BigDecimal amount) {
        return Transaction.builder().type("DEBIT").transactionType("ESSENTIAL").valueDate(date)
                .amount(amount).isHidden(false).build();
    }

    private Transaction discretionary(LocalDate date, BigDecimal amount) {
        return Transaction.builder().type("DEBIT").transactionType("DISCRETIONARY").valueDate(date)
                .amount(amount).isHidden(false).build();
    }
}
