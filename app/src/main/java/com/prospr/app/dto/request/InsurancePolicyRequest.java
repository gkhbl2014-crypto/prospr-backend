package com.prospr.app.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Shared shape for both creating and updating a MANUAL insurance policy. */
@Getter
@Setter
public class InsurancePolicyRequest {

    @NotBlank
    private String insuranceType;

    /** INDIVIDUAL | FAMILY_FLOATER | TERM | WHOLE_LIFE | ENDOWMENT | OTHER */
    private String policyType;

    @NotBlank
    private String policyNumber;

    private String insurerName;
    private String policyName;
    private BigDecimal sumInsured;
    private BigDecimal sumAssured;
    private BigDecimal premiumAmount;
    private String premiumFrequency;
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private LocalDate maturityDate;
    private LocalDate nextPremiumDueDate;
    private String policyStatus;
    private String nomineeName;

    /** Only meaningful when policyType=FAMILY_FLOATER; each id must be a member of the caller's family. */
    private List<UUID> coveredMemberIds;
}
