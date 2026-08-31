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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Member-configurable Safety Net assumptions. Never hardcode these into calculation services -
 * always read them from here (falling back to the documented defaults only when no row exists yet).
 */
@Entity
@Table(name = "safety_net_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class SafetyNetSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, unique = true)
    @JsonIgnore
    @ToString.Exclude
    private Member member;

    @Column(name = "emergency_fund_target_months", nullable = false)
    private Integer emergencyFundTargetMonths;

    @Column(name = "essential_expense_lookback_months", nullable = false)
    private Integer essentialExpenseLookbackMonths;

    @Column(name = "income_replacement_years", precision = 5, scale = 2)
    private BigDecimal incomeReplacementYears;

    @Column(name = "future_financial_goals_amount", precision = 15, scale = 2)
    private BigDecimal futureFinancialGoalsAmount;

    @Column(name = "assets_available_for_dependents", precision = 15, scale = 2)
    private BigDecimal assetsAvailableForDependents;

    /** No Liability entity exists in this app yet, so this is a manual input. */
    @Column(name = "outstanding_liabilities_amount", precision = 15, scale = 2)
    private BigDecimal outstandingLiabilitiesAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
