package com.prospr.app.service.statement;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.prospr.app.entity.Transaction;
import com.prospr.app.service.statement.ImportDuplicateDetectionService.DuplicateCheck;

class ImportDuplicateDetectionServiceTest {

    private final ImportDuplicateDetectionService service = new ImportDuplicateDetectionService();

    private Transaction existing(String txnId, String reference, LocalDate date, String type, String amount,
                                  String source) {
        return Transaction.builder()
                .txnId(txnId)
                .reference(reference)
                .valueDate(date)
                .type(type)
                .amount(new BigDecimal(amount))
                .source(source)
                .build();
    }

    private ParsedTransactionRow row(LocalDate date, String type, String amount, String reference) {
        return new ParsedTransactionRow(date, "narration", null, null, new BigDecimal(amount), null, type, reference);
    }

    @Test
    void exactMatchOnDateAmountTypeIsFlagged() {
        Transaction existing = existing("TXN1", null, LocalDate.of(2023, 4, 1), "DEBIT", "500.00", "SETU");
        ParsedTransactionRow parsed = row(LocalDate.of(2023, 4, 1), "DEBIT", "500.00", null);

        DuplicateCheck result = service.check(parsed, List.of(existing));

        assertThat(result.possibleDuplicate()).isTrue();
        assertThat(result.duplicateReason()).isEqualTo("MATCHES_DATE_AMOUNT_TYPE");
    }

    @Test
    void noMatchWhenAmountDiffers() {
        Transaction existing = existing("TXN1", null, LocalDate.of(2023, 4, 1), "DEBIT", "500.00", "SETU");
        ParsedTransactionRow parsed = row(LocalDate.of(2023, 4, 1), "DEBIT", "600.00", null);

        DuplicateCheck result = service.check(parsed, List.of(existing));

        assertThat(result.possibleDuplicate()).isFalse();
        assertThat(result.duplicateReason()).isNull();
    }

    @Test
    void noMatchWhenTypeDiffersEvenIfDateAndAmountMatch() {
        Transaction existing = existing("TXN1", null, LocalDate.of(2023, 4, 1), "DEBIT", "500.00", "SETU");
        ParsedTransactionRow parsed = row(LocalDate.of(2023, 4, 1), "CREDIT", "500.00", null);

        assertThat(service.check(parsed, List.of(existing)).possibleDuplicate()).isFalse();
    }

    @Test
    void referenceNumberMatchIsFlaggedEvenWithDifferentAmount() {
        Transaction existing = existing("TXN1", "REF-999", LocalDate.of(2023, 4, 1), "DEBIT", "500.00", "SETU");
        ParsedTransactionRow parsed = row(LocalDate.of(2023, 5, 15), "CREDIT", "9999.00", "REF-999");

        DuplicateCheck result = service.check(parsed, List.of(existing));

        assertThat(result.possibleDuplicate()).isTrue();
        assertThat(result.duplicateReason()).isEqualTo("MATCHES_EXISTING_REFERENCE");
    }

    @Test
    void referenceMatchesEitherReferenceOrTxnIdField() {
        Transaction existing = existing("REF-999", null, LocalDate.of(2023, 4, 1), "DEBIT", "500.00", "MANUAL");
        ParsedTransactionRow parsed = row(LocalDate.of(2023, 5, 15), "CREDIT", "9999.00", "REF-999");

        assertThat(service.check(parsed, List.of(existing)).possibleDuplicate()).isTrue();
    }

    @Test
    void matchesAgainstBothSetuAndManualSourcedRows() {
        Transaction setuTxn = existing("TXN1", null, LocalDate.of(2023, 4, 1), "DEBIT", "500.00", "SETU");
        Transaction manualTxn = existing("MANUAL-1", null, LocalDate.of(2023, 4, 2), "DEBIT", "700.00", "MANUAL");
        ParsedTransactionRow parsed = row(LocalDate.of(2023, 4, 2), "DEBIT", "700.00", null);

        DuplicateCheck result = service.check(parsed, List.of(setuTxn, manualTxn));

        assertThat(result.possibleDuplicate()).isTrue();
    }

    @Test
    void noExistingTransactionsMeansNoDuplicate() {
        ParsedTransactionRow parsed = row(LocalDate.of(2023, 4, 1), "DEBIT", "500.00", null);

        assertThat(service.check(parsed, List.of()).possibleDuplicate()).isFalse();
    }
}
