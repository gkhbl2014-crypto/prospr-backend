package com.prospr.app.entity;

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
 * A member's own personal note ({@code label}, e.g. "Oil") and/or structured purpose ({@code purpose},
 * e.g. SHARED_EXPENSE) for a recurring counterparty, keyed by
 * {@link com.prospr.app.service.CounterpartyKeyResolver}'s derived {@code counterpartyKey} rather
 * than any single transaction id - one row here transparently applies to every past AND future
 * transaction that resolves to the same key, with no per-transaction fan-out to maintain. Personal
 * to the member who set it (never global, unlike {@link MerchantAlias}, and never shared with other
 * family members' view of the same counterparty) - a shared VPA can mean something different to two
 * different people. {@code label} and {@code purpose} are independently settable: a member can name a
 * counterparty without answering "how do you use payments to them" and vice versa.
 */
@Entity
@Table(name = "member_transaction_label")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class MemberTransactionLabel {

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

    @Column(name = "counterparty_key", nullable = false)
    private String counterpartyKey;

    /** Free-text personal note, e.g. "Oil". Nullable - a row can exist purely to carry {@code purpose}. */
    @Column(name = "label")
    private String label;

    /** PERSONAL_TRANSFER|SHARED_EXPENSE|REPAYMENT|HOUSEHOLD|GIFT|INVESTMENT|OTHER - the member's answer
     *  to "how do you usually use payments to X". Nullable - a row can exist purely to carry {@code label}. */
    @Column(name = "purpose")
    private String purpose;

    /** How many of the member's transactions matched this counterparty at the time {@code purpose} was
     *  last set - display context only ("based on 3 payments"), never re-derived after the fact. */
    @Column(name = "evidence_count", nullable = false)
    private Integer evidenceCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
