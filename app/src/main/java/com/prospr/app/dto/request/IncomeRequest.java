package com.prospr.app.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncomeRequest {

    @NotBlank
    private String incomeType;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal monthlyAmount;

    @NotNull
    @Pattern(regexp = "MONTHLY|ANNUAL|ONE_TIME", message = "must be MONTHLY, ANNUAL, or ONE_TIME")
    private String frequency;
}
