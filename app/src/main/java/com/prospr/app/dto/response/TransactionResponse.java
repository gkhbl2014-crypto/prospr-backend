package com.prospr.app.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID memberId;
    private String maskedAccountNumber;
    private String txnId;
    private String mode;
    private String type;
    private BigDecimal amount;
    private BigDecimal transactionalBalance;
    private String narration;
    private String reference;
    private LocalDate valueDate;
    private OffsetDateTime transactionTimestamp;
    /** Owner-controlled: the owner has chosen to exclude this transaction from the shared family
     *  activity list. Still returned in full here (never filtered out of this API) so aggregate
     *  calculations that read this same list - net income/expenditure, balances, insights - keep
     *  counting it; the frontend is responsible for excluding it from list-style displays only. */
    private boolean hidden;
    /** "SETU" (parsed from an AA session) or "MANUAL" (PDF/CSV/XLSX import). */
    private String source;
    /** Fine-grained code, e.g. "FOOD_DELIVERY" - a user's own category override, if any, else the
     *  system-derived category. Null if never categorized or LOW-confidence (deliberately left
     *  Unclassified rather than guessed). */
    private String effectiveCategory;
    /** One of the 20 user-facing top-level names ("Food & Dining", "Loan/EMI", ...), derived from
     *  effectiveCategory via CategoryTaxonomy. Never null - "Unclassified" when effectiveCategory is. */
    private String topLevelCategory;
    /** HIGH | MEDIUM | LOW | null (never categorized yet). */
    private String categoryConfidence;
    /** INCOME|EXPENSE|INVESTMENT|DEBT_REPAYMENT|INSURANCE|INTERNAL_TRANSFER|CASH_WITHDRAWAL|REFUND|
     *  OTHER_CREDIT|OTHER_DEBIT|UNKNOWN|null (never classified yet). */
    private String transactionType;
    /** True when a member has manually corrected this transaction's tag via the tag endpoint -
     *  distinguishes a deliberate user correction from the system's own best guess. */
    private boolean manuallyTagged;
    /** The caller's own personal note for this transaction's counterparty (e.g. "Oil"), if they've
     *  set one - null otherwise. Applies to every transaction sharing the same counterparty, not
     *  just the one it was originally set on. */
    private String personalLabel;
}
