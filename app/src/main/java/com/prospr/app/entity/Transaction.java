package com.prospr.app.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
public class Transaction {

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

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "consent_id")
    private String consentId;

    @Column(name = "masked_account_number")
    private String maskedAccountNumber;

    @Column(name = "account_ref")
    private String accountRef;

    @Column(name = "txn_id", nullable = false)
    private String txnId;

    @Column(name = "mode")
    private String mode;

    @Column(name = "type")
    private String type;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transactional_balance", precision = 15, scale = 2)
    private BigDecimal transactionalBalance;

    @Column(name = "narration")
    private String narration;

    @Column(name = "reference")
    private String reference;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "transaction_timestamp")
    private OffsetDateTime transactionTimestamp;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "category")
    private String category;

    @Column(name = "subcategory")
    private String subcategory;

    /** Owner-controlled: excluded from the shared family activity list when true, but still counted
     *  in aggregate calculations (net income/expenditure, balances, insights). */
    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
