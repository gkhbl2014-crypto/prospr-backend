package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * One overall Lifestyle Creep summary for the current month - replaces the old per-category insight
 * list. {@code state} is STABLE|WATCH|INCREASING (see LifestyleTrendEvaluator), not a numeric score.
 * {@code message} is the full ready-to-display narrative; the structured fields below it let the
 * frontend render supporting stat cells/chips without parsing that text.
 */
@Getter
@Builder
@Jacksonized
public class LifestyleCreepSummaryResponse {

    private String state;
    private String periodLabel;
    private boolean monthToDate;
    private BigDecimal currentAmount;
    private BigDecimal baselineAmount;
    private BigDecimal differenceAmount;
    private Double increasePercentage;
    /** 0 when STABLE, 1 when WATCH, 2+ when INCREASING. */
    private int consecutiveElevatedMonths;
    private String message;
    /** The categories that increased the most this month, largest first - empty when STABLE. */
    private List<CategoryAmount> topIncreases;
    /** Null unless a genuinely new discretionary recurring expense was found (WATCH/INCREASING only). */
    private String newRecurringExpenseLabel;
    private BigDecimal newRecurringExpenseAmount;
    /** Null whenever income isn't reliably known for every relevant month - never a guess. */
    private Double incomeGrowthPercent;
}
