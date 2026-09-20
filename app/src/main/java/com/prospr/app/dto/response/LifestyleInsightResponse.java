package com.prospr.app.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/** {@code @Jacksonized} lets this cache cleanly as JSON (Jackson needs a builder it knows how to
 *  deserialize through - a plain {@code @Builder} with no setters/no-arg constructor can't be
 *  reconstructed from JSON otherwise). Additive only; no other behavior change. */
@Getter
@Builder
@Jacksonized
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
