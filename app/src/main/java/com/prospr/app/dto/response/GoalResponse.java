package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoalResponse {

    private UUID id;
    private String goalName;
    private String goalType;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal monthlyContribution;
    private LocalDate targetDate;

    // Computed by GoalCalculator - not stored.
    private BigDecimal progressPercent;
    private BigDecimal remainingAmount;
    private long monthsRemaining;
    private BigDecimal requiredMonthlyContribution;
    /** ON_TRACK | NEEDS_ATTENTION | COMPLETED. */
    private String status;
    private String message;
}
