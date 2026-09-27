package com.prospr.app.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.prospr.app.entity.Transaction;

/**
 * Answers "is this transaction with a business, a person, or genuinely unknown" - the gate that
 * {@link TransactionCategorizationService} is missing: it will happily extract *a name* from a UPI
 * narration, but nothing previously asked whether that name was a lookup-able merchant or a person
 * with no category logic at all. Getting this wrong in the P2P direction is cheap (the transaction
 * just stays unclassified and goes to the review queue); getting it wrong in the MERCHANT direction
 * would silently guess a spending category for someone's name, which is the exact failure this exists
 * to prevent.
 *
 * <p>Deliberately runs AFTER {@link TransactionCategorizationService#categorize}, not instead of it -
 * any transaction that already resolved to a category (merchant DB match or a "specific" fallback-rule
 * brand keyword) is definitionally {@link #MERCHANT}, with no separate merchant-detection logic to
 * keep in sync. Only a transaction categorization couldn't resolve goes through the P2P/UNKNOWN split,
 * using {@link CounterpartyKeyResolver}'s own extraction: a VPA whose handle doesn't contain a known
 * brand keyword, or a plausible "First Last" name pulled from a UPI narration with no VPA, is treated
 * as P2P; anything else (a single ambiguous word, a bare reference number, no signal at all) is
 * UNKNOWN. This is a heuristic, not a verdict - see the class-level caveat in the audit report.
 */
@Component
public class CounterpartyTypeClassifier {

    public static final String MERCHANT = "MERCHANT";
    public static final String P2P = "P2P";
    public static final String UNKNOWN = "UNKNOWN";

    private static final Pattern PERSONAL_NAME_PATTERN = Pattern.compile("^[A-Za-z]+(?:\\s+[A-Za-z]+)+$");
    private static final int MIN_PERSONAL_HANDLE_LENGTH = 3;

    private final CounterpartyKeyResolver counterpartyKeyResolver;
    private final CategoryFallbackRules fallbackRules;

    public CounterpartyTypeClassifier(CounterpartyKeyResolver counterpartyKeyResolver,
                                       CategoryFallbackRules fallbackRules) {
        this.counterpartyKeyResolver = counterpartyKeyResolver;
        this.fallbackRules = fallbackRules;
    }

    public String classify(Transaction txn) {
        if (txn.getEffectiveCategory() != null) {
            return MERCHANT;
        }

        Optional<String> key = counterpartyKeyResolver.resolveKey(txn);
        if (key.isEmpty()) {
            return UNKNOWN;
        }
        String value = key.get();

        if (value.startsWith("VPA:")) {
            String vpa = value.substring("VPA:".length());
            String handle = vpa.contains("@") ? vpa.substring(0, vpa.indexOf('@')) : vpa;
            if (matchesKnownBrandKeyword(handle)) {
                return MERCHANT;
            }
            return looksLikePersonalHandle(handle) ? P2P : UNKNOWN;
        }
        if (value.startsWith("PAYEE:")) {
            String name = value.substring("PAYEE:".length());
            return PERSONAL_NAME_PATTERN.matcher(name.trim()).matches() ? P2P : UNKNOWN;
        }
        // MERCHANT:/NARRATION: fallback keys never resolved to a category above, so there's no
        // further signal here to distinguish person from unrecognized business.
        return UNKNOWN;
    }

    private boolean matchesKnownBrandKeyword(String handle) {
        String lower = handle.toLowerCase(Locale.ROOT);
        return fallbackRules.rules().stream().anyMatch(rule -> lower.contains(rule.keyword()));
    }

    private boolean looksLikePersonalHandle(String handle) {
        return handle.length() >= MIN_PERSONAL_HANDLE_LENGTH
                && handle.chars().allMatch(Character::isLetterOrDigit);
    }
}
