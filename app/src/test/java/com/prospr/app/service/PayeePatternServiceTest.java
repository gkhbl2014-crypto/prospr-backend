package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.prospr.app.config.ClassificationProperties;
import com.prospr.app.entity.Transaction;

class PayeePatternServiceTest {

    private final PayeePatternService service = new PayeePatternService(new ClassificationProperties());

    private Transaction txn(LocalDate date, String amount) {
        return Transaction.builder().valueDate(date).amount(new BigDecimal(amount)).build();
    }

    @Test
    void threeIdenticalMonthlyPaymentsAreConsistentAndMonthly() {
        List<Transaction> transactions = List.of(
                txn(LocalDate.of(2026, 4, 1), "15000"),
                txn(LocalDate.of(2026, 5, 1), "15000"),
                txn(LocalDate.of(2026, 6, 1), "15000"));

        PayeePatternService.Evidence evidence = service.evaluate(transactions);

        assertThat(evidence.occurrences()).isEqualTo(3);
        assertThat(evidence.typicalAmount()).isEqualByComparingTo("15000");
        assertThat(evidence.amountConsistent()).isTrue();
        assertThat(evidence.cadenceGuess()).isEqualTo(PayeePatternService.CADENCE_MONTHLY);
    }

    @Test
    void wildlyVaryingAmountsAreNotConsistentEvenIfMonthly() {
        List<Transaction> transactions = List.of(
                txn(LocalDate.of(2026, 4, 1), "300"),
                txn(LocalDate.of(2026, 5, 1), "500"),
                txn(LocalDate.of(2026, 6, 1), "1200"));

        PayeePatternService.Evidence evidence = service.evaluate(transactions);

        assertThat(evidence.amountConsistent()).isFalse();
    }

    @Test
    void irregularGapsAreNotClassifiedAsMonthly() {
        List<Transaction> transactions = List.of(
                txn(LocalDate.of(2026, 4, 1), "15000"),
                txn(LocalDate.of(2026, 4, 10), "15000"),
                txn(LocalDate.of(2026, 6, 25), "15000"));

        PayeePatternService.Evidence evidence = service.evaluate(transactions);

        assertThat(evidence.cadenceGuess()).isEqualTo(PayeePatternService.CADENCE_IRREGULAR);
    }

    @Test
    void fewerThanTheMinimumOccurrencesReportsNoEvidenceRatherThanGuessing() {
        List<Transaction> transactions = List.of(txn(LocalDate.of(2026, 4, 1), "15000"));

        PayeePatternService.Evidence evidence = service.evaluate(transactions);

        assertThat(evidence.occurrences()).isEqualTo(1);
        assertThat(evidence.typicalAmount()).isNull();
        assertThat(evidence.cadenceGuess()).isNull();
    }
}
