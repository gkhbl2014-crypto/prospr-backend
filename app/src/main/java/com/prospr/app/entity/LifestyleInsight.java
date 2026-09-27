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
 * One overall Lifestyle Creep summary for a member/year/month (category={@code "OVERALL"},
 * insightType={@code "LIFESTYLE_CREEP_SUMMARY"} - see {@link com.prospr.app.service.LifestyleInsightService}).
 * At most one row is kept per (member, year, month, category, insightType) - enforced by a unique
 * constraint; recompute updates it in place instead of inserting duplicates. Older per-category
 * {@code "CATEGORY_SPIKE"} rows from before this redesign may still exist for past months but are
 * never read by current code - harmless leftover history, not migrated or deleted.
 */
@Entity
@Table(name = "lifestyle_insight")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class LifestyleInsight {

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

    /** Denormalized from member.family.id at write time, so family-level rollups can be added later
     *  without changing this table's shape. */
    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "baseline_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal baselineAmount;

    @Column(name = "current_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal currentAmount;

    @Column(name = "difference_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal differenceAmount;

    @Column(name = "increase_percentage")
    private Double increasePercentage;

    /** STABLE | WATCH | INCREASING - see {@link com.prospr.app.service.LifestyleTrendEvaluator}.
     *  Column name kept from the pre-redesign 5-tier severity field to avoid a rename migration. */
    @Column(name = "severity", nullable = false)
    private String severity;

    /** How many months in a row (including this one) have been elevated vs. the baseline - 0 when
     *  STABLE, 1 when WATCH, 2+ when INCREASING. */
    @Column(name = "consecutive_elevated_months", nullable = false)
    private Integer consecutiveElevatedMonths;

    /** Simple "CODE:amount,CODE:amount" serialization of the categories that increased the most this
     *  month, largest first - null when nothing increased (STABLE). No child table for two values. */
    @Column(name = "top_category_increases")
    private String topCategoryIncreases;

    @Column(name = "new_recurring_expense_amount", precision = 15, scale = 2)
    private BigDecimal newRecurringExpenseAmount;

    @Column(name = "new_recurring_expense_label")
    private String newRecurringExpenseLabel;

    /** Null whenever income isn't reliably known for every relevant month - never a guessed value. */
    @Column(name = "income_growth_percent")
    private Double incomeGrowthPercent;

    @Column(name = "insight_type", nullable = false)
    private String insightType;

    @Column(name = "message", nullable = false)
    private String message;

    /** ACTIVE while the condition still holds; RESOLVED once a later recompute no longer qualifies it. */
    @Column(name = "status", nullable = false)
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
