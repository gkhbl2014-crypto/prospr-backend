package com.prospr.app.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ActionableInsightResponse {

    /** REDUCE | RETURN_TO_NORMAL | GOAL_OPPORTUNITY. */
    private String type;
    /** Short display tag - a category name (Dining, Shopping) or a goal name (Marriage). */
    private String label;
    private String message;
    /** The rupee amount used to rank insights - monthly savings, amount freed up, or the monthly gap. */
    private BigDecimal monthlyImpact;
}
