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

    /** HIGH | MEDIUM | LOW - null until categorized. LOW is never paired with a non-null category:
     *  a low-confidence match is left uncategorized rather than guessed (see TransactionCategorizationService). */
    @Column(name = "category_confidence")
    private String categoryConfidence;

    /** MERCHANT_DB | FALLBACK_RULE | USER_OVERRIDE | NONE */
    @Column(name = "category_source")
    private String categorySource;

    /** INCOME|ESSENTIAL|DISCRETIONARY|INVESTMENT|DEBT_REPAYMENT|INSURANCE|INTERNAL_TRANSFER|
     *  CASH_WITHDRAWAL|UNKNOWN - set by TransactionClassificationService, null until classified. */
    @Column(name = "transaction_type")
    private String transactionType;

    /** User-supplied category correction; takes precedence over {@code category} everywhere via
     *  {@link #getEffectiveCategory()} so a correction is respected by every downstream analysis. */
    @Column(name = "user_category_override")
    private String userCategoryOverride;

    /** NECESSARY | LIFESTYLE_CREEP | NOT_SURE - user's own judgement on a discretionary transaction,
     *  overriding the heuristic classification when present. */
    @Column(name = "user_creep_classification")
    private String userCreepClassification;

    @Column(name = "user_override_at")
    private LocalDateTime userOverrideAt;

    /** FK to the recurring_expense row this transaction was matched into, if any. */
    @Column(name = "recurring_expense_id")
    private UUID recurringExpenseId;

    /** Owner-controlled: excluded from the shared family activity list when true, but still counted
     *  in aggregate calculations (net income/expenditure, balances, insights). */
    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden;

    /** "SETU" (parsed from an AA session) or "MANUAL" (PDF/CSV/XLSX import). */
    @Column(name = "source", nullable = false)
    private String source;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** The category every downstream analysis should read: a user correction always wins over the
     *  system-derived category. Not persisted - derived fresh from the two backing fields. */
    public String getEffectiveCategory() {
        return userCategoryOverride != null ? userCategoryOverride : category;
    }
}
