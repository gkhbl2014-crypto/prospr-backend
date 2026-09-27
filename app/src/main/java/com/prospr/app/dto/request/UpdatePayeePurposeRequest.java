package com.prospr.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** {@code counterpartyKey} comes from a {@code GET /api/transactions/review-queue} entry or an
 *  individual transaction's response. {@code purpose} is one of PERSONAL_TRANSFER|SHARED_EXPENSE|
 *  REPAYMENT|RENT|HOUSEHOLD|GIFT|INVESTMENT|OTHER - validated in the controller against that exact
 *  set. {@code label} is optional free text (e.g. "Plumber deposit" when purpose=OTHER) - stored
 *  alongside the purpose on the same counterparty row, independent of it, same as the existing
 *  personal-note {@code /label} endpoint. */
@Getter
@Setter
public class UpdatePayeePurposeRequest {

    @NotBlank
    private String counterpartyKey;

    @NotBlank
    private String purpose;

    @Size(max = 60, message = "must be 60 characters or fewer")
    private String label;
}
