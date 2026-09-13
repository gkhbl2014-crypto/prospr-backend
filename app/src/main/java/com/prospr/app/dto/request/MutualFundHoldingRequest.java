package com.prospr.app.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Shared shape for both creating and updating a MANUAL mutual fund holding. */
@Getter
@Setter
public class MutualFundHoldingRequest {

    @NotBlank
    private String amc;

    private String registrar;
    private String schemeCode;
    private String schemeOption;

    /** Nullable - a manually-entered holding may not have a known ISIN. */
    private String isin;
    private String isinDescription;
    private String folioNo;

    @NotNull
    private BigDecimal costValue;

    private BigDecimal currentValue;
    private BigDecimal closingUnits;
    private BigDecimal nav;
    private LocalDate navDate;
    private LocalDate investmentDate;
}
