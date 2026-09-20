package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.config.RecurringExpenseProperties;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.RecurringExpense;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.RecurringExpenseRepository;
import com.prospr.app.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseDetectionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private RecurringExpenseRepository recurringExpenseRepository;

    private final RecurringExpenseProperties properties = new RecurringExpenseProperties();

    private RecurringExpenseDetectionService service() {
        return new RecurringExpenseDetectionService(transactionRepository, recurringExpenseRepository, properties);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private Transaction txn(String merchant, String category, BigDecimal amount, LocalDate date, String narration) {
        return Transaction.builder().type("DEBIT").merchantName(merchant).category(category)
                .amount(amount).valueDate(date).narration(narration).build();
    }

    private void stubNoExistingRecurring() {
        when(recurringExpenseRepository.findByMemberIdAndMerchantKeyAndCategory(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void detectsMonthlyRentAtExactlyThreeOccurrences() {
        Member member = member();
        stubNoExistingRecurring();
        LocalDate now = LocalDate.now();
        List<Transaction> history = List.of(
                txn("Landlord", "RENT", new BigDecimal("20000"), now.minusMonths(2), "RENT"),
                txn("Landlord", "RENT", new BigDecimal("20000"), now.minusMonths(1), "RENT"),
                txn("Landlord", "RENT", new BigDecimal("20000"), now, "RENT"));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        org.mockito.ArgumentCaptor<RecurringExpense> captor = org.mockito.ArgumentCaptor.forClass(RecurringExpense.class);
        verify(recurringExpenseRepository).save(captor.capture());
        RecurringExpense saved = captor.getValue();
        assertThat(saved.getClassification()).isEqualTo("RENT");
        assertThat(saved.getCadence()).isEqualTo("MONTHLY");
        assertThat(saved.getOccurrencesCount()).isEqualTo(3);
        assertThat(saved.getTypicalAmount()).isEqualByComparingTo("20000");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void amountJitterWithinTenPercentIsStillRecurring() {
        Member member = member();
        stubNoExistingRecurring();
        // 20000, 21500 (+7.5%), 19000 (-5%) - all within the default 10% tolerance band of the median.
        List<Transaction> history = List.of(
                txn("Gym", "FITNESS", new BigDecimal("20000"), LocalDate.of(2026, 1, 5), "GYM"),
                txn("Gym", "FITNESS", new BigDecimal("21500"), LocalDate.of(2026, 2, 5), "GYM"),
                txn("Gym", "FITNESS", new BigDecimal("19000"), LocalDate.of(2026, 3, 5), "GYM"));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        verify(recurringExpenseRepository).save(any());
    }

    @Test
    void amountJitterBeyondTenPercentIsNotRecurring() {
        Member member = member();
        // 20000, 30000 (+50%), 20000 - well outside tolerance.
        List<Transaction> history = List.of(
                txn("Shop", "SHOPPING", new BigDecimal("20000"), LocalDate.of(2026, 1, 5), "SHOP"),
                txn("Shop", "SHOPPING", new BigDecimal("30000"), LocalDate.of(2026, 2, 5), "SHOP"),
                txn("Shop", "SHOPPING", new BigDecimal("20000"), LocalDate.of(2026, 3, 5), "SHOP"));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        verify(recurringExpenseRepository, never()).save(any());
    }

    @Test
    void intervalJitterBeyondFourDaysIsNotMonthlyRecurring() {
        Member member = member();
        // Gaps of 30 and 45 days - the second gap is well outside the +-4 day monthly tolerance,
        // and also outside quarterly/annual bands, so no cadence matches at all.
        List<Transaction> history = List.of(
                txn("Landlord", "RENT", new BigDecimal("20000"), LocalDate.of(2026, 1, 1), "RENT"),
                txn("Landlord", "RENT", new BigDecimal("20000"), LocalDate.of(2026, 1, 31), "RENT"),
                txn("Landlord", "RENT", new BigDecimal("20000"), LocalDate.of(2026, 3, 17), "RENT"));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        verify(recurringExpenseRepository, never()).save(any());
    }

    @Test
    void detectsQuarterlyInsurancePremium() {
        Member member = member();
        stubNoExistingRecurring();
        List<Transaction> history = List.of(
                txn("LIC", "INSURANCE", new BigDecimal("5000"), LocalDate.of(2026, 1, 5), "LIC PREMIUM"),
                txn("LIC", "INSURANCE", new BigDecimal("5000"), LocalDate.of(2026, 4, 4), "LIC PREMIUM"),
                txn("LIC", "INSURANCE", new BigDecimal("5000"), LocalDate.of(2026, 7, 6), "LIC PREMIUM"));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        org.mockito.ArgumentCaptor<RecurringExpense> captor = org.mockito.ArgumentCaptor.forClass(RecurringExpense.class);
        verify(recurringExpenseRepository).save(captor.capture());
        assertThat(captor.getValue().getCadence()).isEqualTo("QUARTERLY");
        assertThat(captor.getValue().getClassification()).isEqualTo("INSURANCE_PREMIUM");
    }

    @Test
    void marksPatternLapsedWhenLastOccurrenceIsLongOverdue() {
        Member member = member();
        stubNoExistingRecurring();
        LocalDate longAgo = LocalDate.now().minusMonths(6);
        List<Transaction> history = List.of(
                txn("OldSub", "SUBSCRIPTIONS", new BigDecimal("500"), longAgo.minusMonths(2), "SUB"),
                txn("OldSub", "SUBSCRIPTIONS", new BigDecimal("500"), longAgo.minusMonths(1), "SUB"),
                txn("OldSub", "SUBSCRIPTIONS", new BigDecimal("500"), longAgo, "SUB"));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        org.mockito.ArgumentCaptor<RecurringExpense> captor = org.mockito.ArgumentCaptor.forClass(RecurringExpense.class);
        verify(recurringExpenseRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("LAPSED");
    }

    @Test
    void fewerThanMinOccurrencesIsNeverStored() {
        Member member = member();
        List<Transaction> history = List.of(
                txn("Landlord", "RENT", new BigDecimal("20000"), LocalDate.of(2026, 1, 1), "RENT"),
                txn("Landlord", "RENT", new BigDecimal("20000"), LocalDate.of(2026, 2, 1), "RENT"));
        when(transactionRepository.findByMemberIdOrderByTransactionTimestampDesc(member.getId())).thenReturn(history);

        service().recomputeForMember(member);

        verify(recurringExpenseRepository, never()).save(any());
    }
}
