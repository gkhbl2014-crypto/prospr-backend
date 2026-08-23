package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InsuranceResponse {

    private UUID id;
    private String maskedPolicyNumber;
    private String insuranceType;
    private String policyNumber;
    private String insurerName;
    private String policyName;
    private BigDecimal sumAssured;
    private BigDecimal premiumAmount;
    private String premiumFrequency;
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private LocalDate maturityDate;
    private LocalDate nextPremiumDueDate;
    private String policyStatus;
    private String nomineeName;
}
