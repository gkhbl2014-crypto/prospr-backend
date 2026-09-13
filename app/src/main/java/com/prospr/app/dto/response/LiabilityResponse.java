package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LiabilityResponse {

    private UUID id;
    private UUID memberId;
    private String loanType;
    private String lenderName;
    private BigDecimal outstandingAmount;
    private BigDecimal emiAmount;
    private BigDecimal interestRate;
    private LocalDate startDate;
    private LocalDate endDate;
    private String source;
}
