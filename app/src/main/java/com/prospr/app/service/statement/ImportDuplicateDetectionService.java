package com.prospr.app.service.statement;

import java.util.List;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.Transaction;

/**
 * Exact-match duplicate detection only - fuzzy narration matching is explicitly out of scope.
 * Flags are advisory: the caller decides whether to import a flagged row anyway.
 */
@Service
public class ImportDuplicateDetectionService {

    public record DuplicateCheck(boolean possibleDuplicate, String duplicateReason) {

        static final DuplicateCheck NONE = new DuplicateCheck(false, null);
    }

    /** Checks against every transaction the member already has, Setu- and MANUAL-sourced alike. */
    public DuplicateCheck check(ParsedTransactionRow row, List<Transaction> existingTransactions) {
        if (row.reference() != null && !row.reference().isBlank()) {
            boolean referenceMatch = existingTransactions.stream().anyMatch(existing ->
                    row.reference().equals(existing.getReference()) || row.reference().equals(existing.getTxnId()));
            if (referenceMatch) {
                return new DuplicateCheck(true, "MATCHES_EXISTING_REFERENCE");
            }
        }

        boolean dateAmountTypeMatch = existingTransactions.stream().anyMatch(existing ->
                row.valueDate() != null && row.valueDate().equals(existing.getValueDate())
                        && row.type() != null && row.type().equalsIgnoreCase(existing.getType())
                        && row.amount() != null && existing.getAmount() != null
                        && row.amount().compareTo(existing.getAmount()) == 0);
        if (dateAmountTypeMatch) {
            return new DuplicateCheck(true, "MATCHES_DATE_AMOUNT_TYPE");
        }

        return DuplicateCheck.NONE;
    }
}
