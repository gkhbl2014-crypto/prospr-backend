package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.Member;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class EssentialExpenseServiceTest {

    private static final YearMonth AS_OF = YearMonth.of(2026, 8);

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionCategorizationService categorizationService;

    private final EssentialCategoryCatalog catalog = new EssentialCategoryCatalog();

    private EssentialExpenseService service() {
        return new EssentialExpenseService(transactionRepository, categorizationService, catalog);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private Transaction txn(LocalDate valueDate, String type, String category, BigDecimal amount) {
        return Transaction.builder().valueDate(valueDate).type(type).category(category).amount(amount).build();
    }

    @Test
    void noTransactionsReturnsNullAverage() {
        Member member = member();
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of());

        EssentialExpenseService.Result result = service().computeAverage(member, 3, AS_OF);

        assertThat(result.averageMonthlyEssentialExpense()).isNull();
        assertThat(result.monthsWithData()).isZero();
    }

    @Test
    void noEssentialExpensesInDataMonthsAveragesToZeroNotNull() {
        Member member = member();
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId()))
                .thenReturn(List.of(txn(AS_OF.minusMonths(1).atDay(5), "DEBIT", "SHOPPING", new BigDecimal("2000"))));

        EssentialExpenseService.Result result = service().computeAverage(member, 3, AS_OF);

        assertThat(result.averageMonthlyEssentialExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.monthsWithData()).isEqualTo(1);
    }

    @Test
    void threeCompletedMonthsAverageCorrectly() {
        Member member = member();
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                txn(AS_OF.minusMonths(1).atDay(2), "DEBIT", "RENT", new BigDecimal("20000")),
                txn(AS_OF.minusMonths(2).atDay(2), "DEBIT", "RENT", new BigDecimal("20000")),
                txn(AS_OF.minusMonths(3).atDay(2), "DEBIT", "RENT", new BigDecimal("20000")),
                txn(AS_OF.minusMonths(1).atDay(3), "DEBIT", "GROCERIES", new BigDecimal("4000")),
                // Outside the 3-month lookback window - must not affect the average.
                txn(AS_OF.minusMonths(4).atDay(1), "DEBIT", "RENT", new BigDecimal("999999"))));

        EssentialExpenseService.Result result = service().computeAverage(member, 3, AS_OF);

        // (24000 + 20000 + 20000) / 3 = 21333.33
        assertThat(result.averageMonthlyEssentialExpense()).isEqualByComparingTo("21333.33");
        assertThat(result.monthsWithData()).isEqualTo(3);
    }

    @Test
    void missingOneMonthOfDataExcludesItFromTheAverageRatherThanCountingAsZero() {
        Member member = member();
        // The middle lookback month (2 months ago) has no transactions at all.
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                txn(AS_OF.minusMonths(1).atDay(2), "DEBIT", "RENT", new BigDecimal("20000")),
                txn(AS_OF.minusMonths(3).atDay(2), "DEBIT", "RENT", new BigDecimal("10000"))));

        EssentialExpenseService.Result result = service().computeAverage(member, 3, AS_OF);

        assertThat(result.monthsWithData()).isEqualTo(2);
        assertThat(result.averageMonthlyEssentialExpense()).isEqualByComparingTo("15000.00");
    }

    @Test
    void uncategorizedDebitsAreExcludedFromTheEssentialSum() {
        Member member = member();
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(List.of());
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(List.of(
                txn(AS_OF.minusMonths(1).atDay(2), "DEBIT", null, new BigDecimal("5000")),
                txn(AS_OF.minusMonths(1).atDay(3), "DEBIT", "RENT", new BigDecimal("15000"))));

        EssentialExpenseService.Result result = service().computeAverage(member, 3, AS_OF);

        assertThat(result.averageMonthlyEssentialExpense()).isEqualByComparingTo("15000.00");
    }

    @Test
    void categorizesUncategorizedTransactionsBeforeComputing() {
        Member member = member();
        Transaction uncategorized = txn(AS_OF.minusMonths(1).atDay(1), "DEBIT", null, new BigDecimal("1000"));
        List<Transaction> uncategorizedList = List.of(uncategorized);
        when(transactionRepository.findByMemberIdAndCategoryIsNull(member.getId())).thenReturn(uncategorizedList);
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(uncategorizedList);

        service().computeAverage(member, 3, AS_OF);

        verify(categorizationService).categorize(uncategorizedList);
        verify(transactionRepository).saveAll(uncategorizedList);
    }
}
