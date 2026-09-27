package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

/** One counterparty group in the "N transactions may need your help" queue - answering this once
 *  (via POST payee/purpose) resolves every transaction it represents, not just one. */
@Getter
@Builder
public class ReviewQueueEntryResponse {
    private String counterpartyKey;
    private String displayName;
    private int transactionCount;
    private BigDecimal totalAmount;
    private LocalDate earliestDate;
    private LocalDate latestDate;
    /** Null when there aren't enough occurrences yet for pattern evidence to mean anything. */
    private Integer patternOccurrences;
    private BigDecimal patternTypicalAmount;
    private boolean patternAmountConsistent;
    /** MONTHLY | IRREGULAR | null (not enough data). */
    private String patternCadenceGuess;
}
