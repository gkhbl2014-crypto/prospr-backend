package com.prospr.app.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * A detected recurring payment (rent/EMI/insurance premium/subscription/SIP/utility/other) for one
 * member, keyed by normalized merchant + category. Recomputed and upserted in place on every
 * analysis run - like {@link LifestyleBaseline}, no history of past detections is retained, only the
 * current state. "Detected" only - never treated as a confirmed liability (see
 * {@code detectionStatus}).
 */
@Entity
@Table(name = "recurring_expense")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class RecurringExpense {

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

    @Column(name = "merchant_key", nullable = false)
    private String merchantKey;

    @Column(name = "category", nullable = false)
    private String category;

    /** RENT | EMI | INSURANCE_PREMIUM | SUBSCRIPTION | SIP | UTILITY | OTHER */
    @Column(name = "classification", nullable = false)
    private String classification;

    @Column(name = "typical_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal typicalAmount;

    @Column(name = "amount_tolerance_percent", precision = 5, scale = 2, nullable = false)
    private BigDecimal amountTolerancePercent;

    /** MONTHLY | QUARTERLY | ANNUAL */
    @Column(name = "cadence", nullable = false)
    private String cadence;

    @Column(name = "occurrences_count", nullable = false)
    private Integer occurrencesCount;

    @Column(name = "first_seen_date", nullable = false)
    private LocalDate firstSeenDate;

    @Column(name = "last_seen_date", nullable = false)
    private LocalDate lastSeenDate;

    @Column(name = "next_expected_date")
    private LocalDate nextExpectedDate;

    /** HIGH | MEDIUM - LOW is never persisted, "not enough evidence" is silence, not noise. */
    @Column(name = "confidence", nullable = false)
    private String confidence;

    /** ACTIVE | LAPSED */
    @Column(name = "status", nullable = false)
    private String status;

    /** DETECTED only in v1 - reserved for a future user-CONFIRMED state. */
    @Column(name = "detection_status", nullable = false)
    private String detectionStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_calculated_at", nullable = false)
    private LocalDateTime lastCalculatedAt;
}
