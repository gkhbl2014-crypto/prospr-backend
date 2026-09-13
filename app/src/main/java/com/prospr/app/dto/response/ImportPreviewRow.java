package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImportPreviewRow {

    /** Minted server-side purely so the frontend has a stable key - not persisted anywhere. */
    private String clientRowId;
    private LocalDate valueDate;
    private String narration;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal amount;
    private String type;
    private BigDecimal balance;
    private String reference;
    private boolean possibleDuplicate;
    /** null, or "MATCHES_EXISTING_REFERENCE" | "MATCHES_DATE_AMOUNT_TYPE" */
    private String duplicateReason;
}
