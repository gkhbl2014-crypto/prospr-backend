package com.prospr.app.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Reusable keyword -> canonical merchant name mapping used by {@link com.prospr.app.service.MerchantNormalizationService}.
 * A row matches a transaction when its narration (lowercased) contains {@code rawKeyword}. Mirrors
 * {@link MerchantCategory}'s shape so admin/DB-driven overrides can extend merchant normalization the
 * same way {@code merchant_category} already extends categorization.
 */
@Entity
@Table(name = "merchant_alias")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class MerchantAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "raw_keyword", nullable = false, unique = true)
    private String rawKeyword;

    @Column(name = "canonical_name", nullable = false)
    private String canonicalName;

    @Column(name = "category")
    private String category;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
