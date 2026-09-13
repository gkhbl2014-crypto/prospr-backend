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
public class FinancialGoalRequest {

    @NotBlank
    private String goalName;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal targetAmount;

    /** Nullable - defaults to zero when omitted. */
    private BigDecimal currentAmount;

    private LocalDate targetDate;
}
