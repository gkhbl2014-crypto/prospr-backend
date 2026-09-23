package com.prospr.app.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.entity.MerchantAlias;
import com.prospr.app.entity.MerchantCategory;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MerchantCategoryRepository;

/**
 * Turns a raw transaction narration into merchantName/category/subcategory/confidence.
 *
 * Pipeline: extract+normalize a merchant name from the narration -> look the raw narration up (as a
 * substring match) against the {@code merchant_category} table -> if nothing matches, fall back to
 * the small keyword rule list in {@link CategoryFallbackRules} -> otherwise leave category null
 * (uncategorized transactions are simply excluded from lifestyle sums downstream).
 *
 * A DB-table match is always HIGH confidence. A fallback-rule match is MEDIUM confidence when the
 * rule is marked {@code specific} (a brand name, or a curated financial-purpose term Safety Net's
 * essential-expense calculation depends on), or LOW confidence when the rule is a generic dictionary
 * word ({@code specific=false}) - a LOW-confidence match is deliberately NOT assigned a category, to
 * avoid guessing wrong on an ambiguous signal (e.g. "cafe" alone doesn't reliably mean dining).
 *
 * The original raw narration on the transaction is never modified.
 */
@Service
public class TransactionCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategorizationService.class);
    private static final String DEBIT = "DEBIT";
    private static final String CREDIT = "CREDIT";
    private static final String SALARY_INCOME = "SALARY_INCOME";
    private static final String REFUND = "REFUND";
    private static final Set<String> SALARY_KEYWORDS = Set.of("salary", "sal credit", "salcredit", "payroll");
    private static final Set<String> REFUND_KEYWORDS = Set.of("refund", "reversal", "chargeback", "cashback");

    public static final String CONFIDENCE_HIGH = "HIGH";
    public static final String CONFIDENCE_MEDIUM = "MEDIUM";
    public static final String CONFIDENCE_LOW = "LOW";

    public static final String SOURCE_MERCHANT_DB = "MERCHANT_DB";
    public static final String SOURCE_FALLBACK_RULE = "FALLBACK_RULE";
    public static final String SOURCE_NONE = "NONE";

    private final MerchantCategoryRepository merchantCategoryRepository;
    private final CategoryFallbackRules fallbackRules;
    private final MerchantNormalizationService merchantNormalizationService;

    public TransactionCategorizationService(MerchantCategoryRepository merchantCategoryRepository,
                                              CategoryFallbackRules fallbackRules,
                                              MerchantNormalizationService merchantNormalizationService) {
        this.merchantCategoryRepository = merchantCategoryRepository;
        this.fallbackRules = fallbackRules;
        this.merchantNormalizationService = merchantNormalizationService;
    }

    /**
     * Categorizes every transaction in the list that doesn't already have a category. Returns how
     * many were newly categorized (i.e. matched a known category, not just merchant-extracted).
     */
    public int categorize(List<Transaction> transactions) {
        if (transactions.isEmpty()) {
            return 0;
        }
        List<MerchantCategory> known = merchantCategoryRepository.findAll();
        List<MerchantAlias> knownAliases = merchantNormalizationService.loadKnownAliases();
        int categorized = 0;
        for (Transaction txn : transactions) {
            if (txn.getCategory() != null) {
                continue;
            }
            String narration = txn.getNarration();
            txn.setMerchantName(merchantNormalizationService.normalize(narration, knownAliases)
                    .orElseGet(() -> extractMerchant(narration)));

            String haystack = narration == null ? "" : narration.toLowerCase(Locale.ROOT);

            if (CREDIT.equalsIgnoreCase(txn.getType())) {
                if (matchesSalary(haystack)) {
                    txn.setCategory(SALARY_INCOME);
                    txn.setSubcategory(null);
                    txn.setCategoryConfidence(CONFIDENCE_MEDIUM);
                    txn.setCategorySource(SOURCE_FALLBACK_RULE);
                    categorized++;
                } else if (matchesRefund(haystack)) {
                    // A refund/reversal/chargeback/cashback credit must never be counted as income -
                    // it's money coming back, not money earned.
                    txn.setCategory(REFUND);
                    txn.setSubcategory(null);
                    txn.setCategoryConfidence(CONFIDENCE_MEDIUM);
                    txn.setCategorySource(SOURCE_FALLBACK_RULE);
                    categorized++;
                } else {
                    txn.setCategoryConfidence(null);
                    txn.setCategorySource(SOURCE_NONE);
                }
                continue;
            }

            // Only debit/expense transactions are categorized against the merchant/fallback rules;
            // credits other than salary keep category=null so they can never be summed as spend.
            if (!DEBIT.equalsIgnoreCase(txn.getType())) {
                continue;
            }

            Optional<Categorization> knownMatch = matchKnown(known, haystack);
            if (knownMatch.isPresent()) {
                txn.setCategory(knownMatch.get().category());
                txn.setSubcategory(knownMatch.get().subcategory());
                txn.setCategoryConfidence(CONFIDENCE_HIGH);
                txn.setCategorySource(SOURCE_MERCHANT_DB);
                categorized++;
                continue;
            }

            Optional<CategoryFallbackRules.Rule> fallbackMatch = matchFallback(haystack);
            if (fallbackMatch.isPresent()) {
                CategoryFallbackRules.Rule rule = fallbackMatch.get();
                if (rule.specific()) {
                    txn.setCategory(rule.category());
                    txn.setSubcategory(rule.subcategory());
                    txn.setCategoryConfidence(CONFIDENCE_MEDIUM);
                    txn.setCategorySource(SOURCE_FALLBACK_RULE);
                    categorized++;
                } else {
                    // Ambiguous generic-word match: don't guess, but still record that we looked.
                    txn.setCategoryConfidence(CONFIDENCE_LOW);
                    txn.setCategorySource(SOURCE_NONE);
                }
            } else {
                txn.setCategoryConfidence(null);
                txn.setCategorySource(SOURCE_NONE);
            }
        }
        log.info("Categorized {}/{} transaction(s)", categorized, transactions.size());
        return categorized;
    }

    private boolean matchesSalary(String haystack) {
        return SALARY_KEYWORDS.stream().anyMatch(haystack::contains);
    }

    private boolean matchesRefund(String haystack) {
        return REFUND_KEYWORDS.stream().anyMatch(haystack::contains);
    }

    private record Categorization(String category, String subcategory) {
    }

    private Optional<Categorization> matchKnown(List<MerchantCategory> known, String haystack) {
        return known.stream()
                .filter(mc -> mc.getMerchantKeyword() != null
                        && haystack.contains(mc.getMerchantKeyword().toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(mc -> mc.getMerchantKeyword().length()))
                .map(mc -> new Categorization(mc.getCategory(), mc.getSubcategory()));
    }

    private Optional<CategoryFallbackRules.Rule> matchFallback(String haystack) {
        return fallbackRules.rules().stream()
                .filter(rule -> haystack.contains(rule.keyword()))
                .max(Comparator.comparingInt(rule -> rule.keyword().length()));
    }

    /**
     * Best-effort merchant extraction, used only when {@link MerchantNormalizationService} doesn't
     * recognize the narration. UPI narrations follow {@code UPI/<ref>/<payee>/<vpa>/<bank>/...}; for
     * anything else, take the first token that looks like a word rather than a reference number.
     */
    private String extractMerchant(String narration) {
        if (narration == null || narration.isBlank()) {
            return null;
        }
        String trimmed = narration.trim();

        if (trimmed.toUpperCase(Locale.ROOT).startsWith("UPI")) {
            String[] parts = trimmed.split("/");
            if (parts.length > 2 && !parts[2].isBlank()) {
                return titleCase(parts[2].trim());
            }
        }

        String[] tokens = trimmed.split("[/\\-\\s]+");
        for (String token : tokens) {
            if (token.length() >= 3 && !token.matches("\\d+") && token.chars().anyMatch(Character::isLetter)
                    && !"UPI".equalsIgnoreCase(token) && !"NEFT".equalsIgnoreCase(token)
                    && !"IMPS".equalsIgnoreCase(token) && !"RTGS".equalsIgnoreCase(token)) {
                return titleCase(token);
            }
        }
        return null;
    }

    private String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}
