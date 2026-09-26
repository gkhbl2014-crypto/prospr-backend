package com.prospr.app.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Corrects a transaction's CREDIT/DEBIT direction - for the residual case a parser or the bank's
 *  own statement layout got wrong (see GenericPdfStatementParser's documented first-record gap). */
@Getter
@Setter
public class UpdateTransactionDirectionRequest {

    @NotBlank
    @Pattern(regexp = "CREDIT|DEBIT", message = "must be CREDIT or DEBIT")
    private String type;
}
