package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** {@code @Jacksonized} on this class and every nested builder below lets the whole tree cache
 *  cleanly as JSON (a plain {@code @Builder} with no setters/no-arg constructor can't be
 *  reconstructed from JSON by Jackson otherwise). Additive only; no other behavior change. */
@Getter
@Builder
@Jacksonized
public class SafetyNetResponse {

    private Health health;
    private Life life;
    private EmergencyFund emergencyFund;
    private List<Insight> insights;
    private LocalDateTime calculatedAt;

    @Getter
    @Builder
    @Jacksonized
    public static class Health {
        private BigDecimal individualCoverage;
        private BigDecimal sharedCoverage;
        private BigDecimal totalCoverage;
        private Integer activePolicyCount;
        private String status;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class Life {
        private BigDecimal totalCoverage;
        private BigDecimal estimatedTarget;
        private BigDecimal estimatedGap;
        private String status;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class EmergencyFund {
        private BigDecimal currentAmount;
        private BigDecimal averageMonthlyEssentialExpense;
        private BigDecimal coverageMonths;
        private Integer targetMonths;
        private BigDecimal targetAmount;
        private BigDecimal gap;
        private String status;
    }

    /** severity: INFO | WARNING | CRITICAL */
    @Getter
    @Builder
    @Jacksonized
    public static class Insight {
        private String type;
        private String severity;
        private String message;
    }
}
