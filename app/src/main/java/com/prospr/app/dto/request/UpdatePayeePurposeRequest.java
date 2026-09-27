package com.prospr.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** {@code counterpartyKey} comes from a {@code GET /api/transactions/review-queue} entry or an
 *  individual transaction's response. {@code purpose} is one of PERSONAL_TRANSFER|SHARED_EXPENSE|
 *  REPAYMENT|HOUSEHOLD|GIFT|INVESTMENT|OTHER - validated in the controller against that exact set. */
@Getter
@Setter
public class UpdatePayeePurposeRequest {

    @NotBlank
    private String counterpartyKey;

    @NotBlank
    private String purpose;
}
