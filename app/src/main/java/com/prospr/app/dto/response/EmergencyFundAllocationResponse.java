package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmergencyFundAllocationResponse {

    private UUID id;
    private String maskedAccountNumber;
    private BigDecimal allocatedAmount;
    private Boolean isActive;
    private String sourceType;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
