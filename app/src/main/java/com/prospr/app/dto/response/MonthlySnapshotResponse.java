package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MonthlySnapshotResponse {

    private Integer year;
    private Integer month;
    /** e.g. "August 2026" */
    private String monthLabel;

    private BigDecimal totalIncome;
    private BigDecimal totalEssential;
    private BigDecimal totalDiscretionary;
    private BigDecimal totalInvestment;
    private BigDecimal totalDebtRepayment;
    private BigDecimal totalInsurance;
    private BigDecimal totalInternalTransfer;
    private BigDecimal totalCashWithdrawal;
    private BigDecimal totalUnknown;
    private BigDecimal totalExpenses;

    /** Null only when hasData=false. */
    private BigDecimal savings;
    /** Null when totalIncome=0 - never shown as a literal 0%. */
    private BigDecimal savingsRate;

    private Integer transactionCount;
    private BigDecimal averageTransactionValue;
    private List<CategoryAmount> topCategories;
    private boolean hasData;

    /** Month-over-month deltas vs. the previous month in the requested range, null for the oldest
     *  month returned (no prior month in range to compare against). */
    private BigDecimal incomeChangeFromPreviousMonth;
    private BigDecimal expensesChangeFromPreviousMonth;
    private BigDecimal savingsRateChangeFromPreviousMonth;

    private LocalDateTime calculatedAt;
}
