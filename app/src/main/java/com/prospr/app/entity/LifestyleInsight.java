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
 * A generated lifestyle-creep insight for one member/year/month/category/insightType. At most one
 * row is kept per that key (enforced by a unique constraint); recompute updates it in place instead
 * of inserting duplicates.
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

    @Column(name = "severity", nullable = false)
    private String severity;

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
