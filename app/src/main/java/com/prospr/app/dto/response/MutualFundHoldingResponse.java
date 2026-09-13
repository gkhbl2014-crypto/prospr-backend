package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MutualFundHoldingResponse {

    private UUID id;
    private UUID memberId;
    private String maskedAccountNumber;
    private BigDecimal costValue;
    private BigDecimal currentValue;
    private String amc;
    private String registrar;
    private String schemeCode;
    private String schemeOption;
    private String isin;
    private String isinDescription;
    private String folioNo;
    private BigDecimal closingUnits;
    private BigDecimal nav;
    private LocalDate navDate;
    private LocalDate investmentDate;
    private String source;
}
