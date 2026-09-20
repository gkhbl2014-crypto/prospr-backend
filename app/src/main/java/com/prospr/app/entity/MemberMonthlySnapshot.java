package com.prospr.app.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One row per member/year/month: the whole month's income/expense/savings picture in one place,
 * distinct from {@link MemberMonthlySummary} (per-category DEBIT sums feeding Lifestyle's rolling
 * baseline). Upserted in place on every recompute - like every other cache table in this codebase,
 * no history of past recomputes for the same month is retained, only the current state.
 */
@Entity
@Table(name = "member_monthly_snapshot")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class MemberMonthlySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Member member;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "total_income", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalIncome;

    @Column(name = "total_essential", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalEssential;

    @Column(name = "total_discretionary", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalDiscretionary;

    @Column(name = "total_investment", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalInvestment;

    @Column(name = "total_debt_repayment", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalDebtRepayment;

    @Column(name = "total_insurance", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalInsurance;

    @Column(name = "total_internal_transfer", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalInternalTransfer;

    @Column(name = "total_cash_withdrawal", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalCashWithdrawal;

    @Column(name = "total_unknown", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalUnknown;

    @Column(name = "total_expenses", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalExpenses;

    /** Null only when hasData=false - a month with genuinely zero transactions has no defined savings. */
    @Column(name = "savings", precision = 15, scale = 2)
    private BigDecimal savings;

    /** Null when totalIncome=0 - an undefined ratio, never displayed as literal 0%. */
    @Column(name = "savings_rate", precision = 6, scale = 2)
    private BigDecimal savingsRate;

    @Column(name = "transaction_count", nullable = false)
    private Integer transactionCount;

    @Column(name = "average_transaction_value", precision = 15, scale = 2)
    private BigDecimal averageTransactionValue;

    /** False = zero transactions existed for this member in this month at all. */
    @Column(name = "has_data", nullable = false)
    private Boolean hasData;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
