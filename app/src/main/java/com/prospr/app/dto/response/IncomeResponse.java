package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IncomeResponse {

    private UUID id;
    private UUID memberId;
    private String incomeType;
    private BigDecimal monthlyAmount;
    private String frequency;
    private String source;
}
