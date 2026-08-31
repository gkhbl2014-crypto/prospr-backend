package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SafetyNetSettingsResponse {

    private UUID id;
    private Integer emergencyFundTargetMonths;
    private Integer essentialExpenseLookbackMonths;
    private BigDecimal incomeReplacementYears;
    private BigDecimal futureFinancialGoalsAmount;
    private BigDecimal assetsAvailableForDependents;
    private BigDecimal outstandingLiabilitiesAmount;
}
