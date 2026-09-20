package com.prospr.app.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

/** A member's "normal" monthly spend per lifestyle category, from the previous N completed months
 *  (N = {@code lifestyle.baseline-months}, currently 6). */
@Entity
@Table(name = "lifestyle_baseline")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class LifestyleBaseline {

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

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "baseline_months", nullable = false)
    private Integer baselineMonths;

    @Column(name = "baseline_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal baselineAmount;

    @Column(name = "minimum_amount", precision = 15, scale = 2)
    private BigDecimal minimumAmount;

    @Column(name = "maximum_amount", precision = 15, scale = 2)
    private BigDecimal maximumAmount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;
}
