package com.prospr.app.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** All fields nullable by design - no hardcoded assumptions belong in the calculation services. */
@Getter
@Setter
public class SafetyNetSettingsRequest {

    @Min(1)
    private Integer emergencyFundTargetMonths;

    @Min(1)
    private Integer essentialExpenseLookbackMonths;

    private BigDecimal incomeReplacementYears;
    private BigDecimal futureFinancialGoalsAmount;
    private BigDecimal assetsAvailableForDependents;
    private BigDecimal outstandingLiabilitiesAmount;
}
