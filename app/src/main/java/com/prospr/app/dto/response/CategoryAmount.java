package com.prospr.app.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder
@Jacksonized
public class CategoryAmount {
    private String category;
    private String topLevelCategory;
    private BigDecimal amount;
}
