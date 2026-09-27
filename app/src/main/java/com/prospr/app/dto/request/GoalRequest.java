package com.prospr.app.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoalRequest {

    @NotBlank
    private String goalName;

    /** One of {@link com.prospr.app.service.GoalType#ALL}. */
    @NotBlank
    private String goalType;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal targetAmount;

    /** Nullable - defaults to zero when omitted. */
    private BigDecimal currentAmount;

    /** Nullable - defaults to zero when omitted. */
    private BigDecimal monthlyContribution;

    @NotNull
    private LocalDate targetDate;
}
