package com.prospr.app.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmergencyFundAllocationRequest {

    /** Required when sourceType=ACCOUNT; must be one of the caller's own linked accounts. */
    private String maskedAccountNumber;

    @NotNull
    @DecimalMin(value = "0.01", message = "must be greater than zero")
    private BigDecimal allocatedAmount;

    /** ACCOUNT | MANUAL */
    @NotBlank
    private String sourceType;

    private String description;

    /** PUT only - ignored on create (allocations always start active). Null means "leave unchanged". */
    private Boolean isActive;
}
