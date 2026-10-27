package com.prospr.app.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * "What if I...?" question. Nothing here is stored - the answer is computed from the member's
 * existing data and returned straight away.
 */
@Getter
@Setter
public class WhatIfRequest {

    /** LOAN: borrow {@code amount}, repay in EMIs. ONE_TIME_SPEND: pay {@code amount} out of savings.
     *  MONTHLY_COST: a new recurring monthly cost of {@code amount}. */
    @NotNull
    @Pattern(regexp = "LOAN|ONE_TIME_SPEND|MONTHLY_COST", message = "must be LOAN, ONE_TIME_SPEND, or MONTHLY_COST")
    private String type;

    @NotNull
    @DecimalMin(value = "1", message = "must be at least 1")
    @DecimalMax(value = "1000000000", message = "is too large")
    private BigDecimal amount;

    /** Yearly interest rate in percent (e.g. 18 for 18%). Only used for LOAN; blank means 0%
     *  (an interest-free pay-later or no-cost EMI). */
    @DecimalMin(value = "0", message = "cannot be negative")
    @DecimalMax(value = "100", message = "must be 100 or less")
    private BigDecimal annualInterestRate;

    /** Number of monthly instalments. Required for LOAN. */
    @Min(value = 1, message = "must be at least 1 month")
    @Max(value = 360, message = "must be 360 months or less")
    private Integer tenureMonths;

    /** Money the member receives each month in cash, which never shows up in a bank statement.
     *  Optional; blank or zero means none. Added to income for this answer only - never stored. */
    @DecimalMin(value = "0", message = "cannot be negative")
    @DecimalMax(value = "1000000000", message = "is too large")
    private BigDecimal extraMonthlyIncome;

    @Size(max = 80)
    private String label;
}
