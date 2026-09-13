package com.prospr.app.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LiabilityRequest {

    @NotBlank
    private String loanType;

    private String lenderName;

    @NotNull
    private BigDecimal outstandingAmount;

    private BigDecimal emiAmount;
    private BigDecimal interestRate;
    private LocalDate startDate;
    private LocalDate endDate;
}
