package com.prospr.app.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryAmount {
    private String category;
    private String topLevelCategory;
    private BigDecimal amount;
}
