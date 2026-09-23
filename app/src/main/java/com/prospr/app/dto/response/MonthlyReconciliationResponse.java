package com.prospr.app.dto.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Diagnostic view answering "does every rupee that moved this month land somewhere classified" -
 * {@code totalCredits}/{@code totalDebits} are the raw sums straight from each transaction's own
 * DEBIT/CREDIT type, {@code reconciled}/{@code difference} compare them against the classified
 * bucket sums. Deliberately carries only month-level totals, never individual transaction narrations.
 */
@Getter
@Builder
@Jacksonized
public class MonthlyReconciliationResponse {
    private String month;
    private Integer transactionCount;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal income;
    private BigDecimal expenses;
    private BigDecimal investments;
    private BigDecimal debtPayments;
    private BigDecimal insurance;
    private BigDecimal cashWithdrawals;
    private BigDecimal refunds;
    private BigDecimal transfers;
    private BigDecimal otherCredits;
    private BigDecimal otherDebits;
    private BigDecimal unclassified;
    private BigDecimal totalCashOutflow;
    private boolean reconciled;
    private BigDecimal difference;
}
