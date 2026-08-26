package com.prospr.app.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LifestyleInsightResponse {

    private String category;
    private String categoryLabel;
    private BigDecimal baselineAmount;
    private BigDecimal currentAmount;
    private BigDecimal differenceAmount;
    private Double increasePercentage;
    private String severity;
    private String message;
    private boolean monthToDate;
    private String periodLabel;
}
