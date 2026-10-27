package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class WhatIfResponse {

    /** COMFORTABLE | TIGHT | RISKY | INSUFFICIENT_DATA. */
    private String verdict;
    private String type;
    private String label;

    /** Extra amount leaving each month (the EMI, or the new monthly cost). Zero for a one-time spend. */
    private BigDecimal monthlyCommitment;
    /** Paid up front out of savings. Zero for a loan or a monthly cost. */
    private BigDecimal upfrontAmount;
    /** Everything this decision costs over its life: EMIs paid, the one-time amount, or 12 months of the cost. */
    private BigDecimal totalCost;
    /** Interest paid on top of the amount borrowed. Zero unless it is a loan. */
    private BigDecimal totalInterest;

    private Position before;
    private Position after;

    /** For a one-time spend: how many months of normal saving it takes to put the money back. */
    private BigDecimal monthsToRebuild;
    /** For a one-time spend larger than the emergency fund: the part that would have to be borrowed. */
    private BigDecimal fundShortfall;

    private List<Reason> reasons;
    /** How many months of history the baseline is built on. */
    private Integer basedOnMonths;
    /** Assumptions the figures rest on, shown under the answer. */
    private List<String> notes;

    @Getter
    @Builder
    public static class Position {
        private BigDecimal monthlyIncome;
        private BigDecimal monthlySavings;
        /** Fraction of income saved (0.18 = 18%); null when income is unknown. */
        private BigDecimal savingsRate;
        private BigDecimal totalEmi;
        /** Fraction of income going to EMIs; null when income is unknown. */
        private BigDecimal emiToIncome;
        private BigDecimal emergencyFundAmount;
        /** Months of essential spending the emergency fund covers; null when essentials are unknown. */
        private BigDecimal emergencyFundMonths;
        /** Monthly amount the goals need that savings would not cover. */
        private BigDecimal goalsShortfall;
    }

    @Getter
    @Builder
    public static class Reason {
        /** RISKY | TIGHT | OK | INFO. */
        private String severity;
        private String message;
    }
}
