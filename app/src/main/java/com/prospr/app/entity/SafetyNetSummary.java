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
 * Cached Safety Net calculation result for one member. Fully recalculable from source data at any
 * time by {@code SafetyNetService.recomputeForMember} - never treat this as the source of truth,
 * only as a read-time cache of it.
 */
@Entity
@Table(name = "safety_net_summary")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class SafetyNetSummary {

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

    @Column(name = "individual_health_coverage", precision = 15, scale = 2, nullable = false)
    private BigDecimal individualHealthCoverage;

    @Column(name = "shared_health_coverage", precision = 15, scale = 2, nullable = false)
    private BigDecimal sharedHealthCoverage;

    @Column(name = "total_health_coverage", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalHealthCoverage;

    @Column(name = "active_health_policy_count", nullable = false)
    private Integer activeHealthPolicyCount;

    @Column(name = "health_coverage_status", nullable = false)
    private String healthCoverageStatus;

    @Column(name = "total_life_coverage", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalLifeCoverage;

    @Column(name = "estimated_life_coverage_target", precision = 15, scale = 2)
    private BigDecimal estimatedLifeCoverageTarget;

    @Column(name = "estimated_life_coverage_gap", precision = 15, scale = 2)
    private BigDecimal estimatedLifeCoverageGap;

    @Column(name = "life_coverage_status", nullable = false)
    private String lifeCoverageStatus;

    @Column(name = "emergency_fund_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal emergencyFundAmount;

    @Column(name = "average_monthly_essential_expense", precision = 15, scale = 2)
    private BigDecimal averageMonthlyEssentialExpense;

    @Column(name = "emergency_fund_coverage_months", precision = 6, scale = 2)
    private BigDecimal emergencyFundCoverageMonths;

    @Column(name = "emergency_fund_target_months", nullable = false)
    private Integer emergencyFundTargetMonths;

    @Column(name = "emergency_fund_target_amount", precision = 15, scale = 2)
    private BigDecimal emergencyFundTargetAmount;

    @Column(name = "emergency_fund_gap", precision = 15, scale = 2)
    private BigDecimal emergencyFundGap;

    @Column(name = "emergency_fund_status", nullable = false)
    private String emergencyFundStatus;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
