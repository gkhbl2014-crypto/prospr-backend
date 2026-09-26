package com.prospr.app.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Blank or null clears the label for this transaction's counterparty entirely. */
@Getter
@Setter
public class UpdateTransactionLabelRequest {

    @Size(max = 60, message = "must be 60 characters or fewer")
    private String label;
}
