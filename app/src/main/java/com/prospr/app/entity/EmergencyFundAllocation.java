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
 * Money a member has explicitly set aside for emergencies - never inferred from a whole account
 * balance. {@code maskedAccountNumber} is a plain string (not a FK) because this app has no
 * first-class "financial account" entity; linked accounts are only ever represented as flat
 * masked-number strings, same convention as {@link Transaction#getMaskedAccountNumber()}.
 */
@Entity
@Table(name = "emergency_fund_allocation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class EmergencyFundAllocation {

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

    @Column(name = "masked_account_number")
    private String maskedAccountNumber;

    @Column(name = "allocated_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal allocatedAmount;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** "ACCOUNT" (tied to a linked bank account) or "MANUAL" (not represented by any linked account). */
    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "description")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
