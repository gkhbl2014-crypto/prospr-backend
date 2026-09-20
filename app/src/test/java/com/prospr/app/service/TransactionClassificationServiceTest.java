package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.prospr.app.entity.Transaction;

class TransactionClassificationServiceTest {

    private final TransactionClassificationService service = new TransactionClassificationService(new LifestyleCategoryCatalog());

    private Transaction txn(String type, String category, BigDecimal amount, LocalDate date, String mode, String account) {
        return Transaction.builder().type(type).category(category).amount(amount).valueDate(date)
                .mode(mode).maskedAccountNumber(account).build();
    }

    @Test
    void mapsEachCategoryToItsExpectedTransactionType() {
        assertThat(classify("RENT")).isEqualTo(TransactionClassificationService.ESSENTIAL);
        assertThat(classify("GROCERIES")).isEqualTo(TransactionClassificationService.ESSENTIAL);
        assertThat(classify("EMI")).isEqualTo(TransactionClassificationService.DEBT_REPAYMENT);
        assertThat(classify("LOAN_PAYMENT")).isEqualTo(TransactionClassificationService.DEBT_REPAYMENT);
        assertThat(classify("INSURANCE")).isEqualTo(TransactionClassificationService.INSURANCE);
        assertThat(classify("INVESTMENT")).isEqualTo(TransactionClassificationService.INVESTMENT);
        assertThat(classify("CASH_WITHDRAWAL")).isEqualTo(TransactionClassificationService.CASH_WITHDRAWAL);
        assertThat(classify("CREDIT_CARD_PAYMENT")).isEqualTo(TransactionClassificationService.INTERNAL_TRANSFER);
        assertThat(classify("SALARY_INCOME")).isEqualTo(TransactionClassificationService.INCOME);
        assertThat(classify("DINING")).isEqualTo(TransactionClassificationService.DISCRETIONARY);
        assertThat(classify("SHOPPING")).isEqualTo(TransactionClassificationService.DISCRETIONARY);
        assertThat(classify(null)).isEqualTo(TransactionClassificationService.UNKNOWN);
    }

    private String classify(String category) {
        Transaction txn = txn("DEBIT", category, new BigDecimal("100"), LocalDate.of(2026, 1, 1), null, null);
        service.classify(List.of(txn));
        return txn.getTransactionType();
    }

    @Test
    void userCategoryOverrideTakesPrecedenceForClassification() {
        Transaction txn = txn("DEBIT", "SHOPPING", new BigDecimal("100"), LocalDate.of(2026, 1, 1), null, null);
        txn.setUserCategoryOverride("RENT");

        service.classify(List.of(txn));

        assertThat(txn.getTransactionType()).isEqualTo(TransactionClassificationService.ESSENTIAL);
    }

    @Test
    void detectsSelfTransferBetweenTwoLinkedAccountsSameAmountNextDay() {
        Transaction debit = txn("DEBIT", "SHOPPING", new BigDecimal("5000"), LocalDate.of(2026, 3, 1), "UPI", "XXXX1111");
        Transaction credit = txn("CREDIT", null, new BigDecimal("5000"), LocalDate.of(2026, 3, 2), null, "XXXX2222");

        service.classify(List.of(debit, credit));

        assertThat(debit.getTransactionType()).isEqualTo(TransactionClassificationService.INTERNAL_TRANSFER);
        assertThat(credit.getTransactionType()).isEqualTo(TransactionClassificationService.INTERNAL_TRANSFER);
    }

    @Test
    void doesNotDetectSelfTransferWithOnlyOneLinkedAccount() {
        // Same amount, transfer-capable mode, but only one distinct masked account number across
        // the member's transactions - manual-only/single-account members can't be told apart from
        // two genuinely unrelated transactions that happen to match.
        Transaction debit = txn("DEBIT", "SHOPPING", new BigDecimal("5000"), LocalDate.of(2026, 3, 1), "UPI", "XXXX1111");
        Transaction credit = txn("CREDIT", null, new BigDecimal("5000"), LocalDate.of(2026, 3, 2), null, "XXXX1111");

        service.classify(List.of(debit, credit));

        assertThat(debit.getTransactionType()).isEqualTo(TransactionClassificationService.DISCRETIONARY);
        assertThat(credit.getTransactionType()).isEqualTo(TransactionClassificationService.UNKNOWN);
    }

    @Test
    void doesNotDetectSelfTransferWhenAmountsDiffer() {
        Transaction debit = txn("DEBIT", "SHOPPING", new BigDecimal("5000"), LocalDate.of(2026, 3, 1), "UPI", "XXXX1111");
        Transaction credit = txn("CREDIT", null, new BigDecimal("4000"), LocalDate.of(2026, 3, 2), null, "XXXX2222");

        service.classify(List.of(debit, credit));

        assertThat(debit.getTransactionType()).isEqualTo(TransactionClassificationService.DISCRETIONARY);
        assertThat(credit.getTransactionType()).isEqualTo(TransactionClassificationService.UNKNOWN);
    }
}
