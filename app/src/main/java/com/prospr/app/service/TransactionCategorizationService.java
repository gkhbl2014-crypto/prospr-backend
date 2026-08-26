package com.prospr.app.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prospr.app.entity.MerchantCategory;
import com.prospr.app.entity.Transaction;
import com.prospr.app.repository.MerchantCategoryRepository;

/**
 * Turns a raw transaction narration into merchantName/category/subcategory.
 *
 * Pipeline: extract a merchant token from the narration -> look it up (as a substring match)
 * against the {@code merchant_category} table -> if nothing matches, fall back to the small
 * keyword rule list in {@link CategoryFallbackRules} -> otherwise leave category null
 * (uncategorized transactions are simply excluded from lifestyle sums downstream).
 *
 * The original raw narration on the transaction is never modified.
 */
@Service
public class TransactionCategorizationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionCategorizationService.class);
    private static final String DEBIT = "DEBIT";

    private final MerchantCategoryRepository merchantCategoryRepository;
    private final CategoryFallbackRules fallbackRules;

    public TransactionCategorizationService(MerchantCategoryRepository merchantCategoryRepository,
                                              CategoryFallbackRules fallbackRules) {
        this.merchantCategoryRepository = merchantCategoryRepository;
        this.fallbackRules = fallbackRules;
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
        int categorized = 0;
        for (Transaction txn : transactions) {
            if (txn.getCategory() != null) {
                continue;
            }
            String narration = txn.getNarration();
            txn.setMerchantName(extractMerchant(narration));

            // Only debit/expense transactions are ever categorized into a spend category; credits
            // (salary, refunds, dividends) keep category=null so they can never be summed as spend.
            if (!DEBIT.equalsIgnoreCase(txn.getType())) {
                continue;
            }

            String haystack = narration == null ? "" : narration.toLowerCase(Locale.ROOT);
            Optional<Categorization> match = matchKnown(known, haystack).or(() -> matchFallback(haystack));
            if (match.isPresent()) {
                txn.setCategory(match.get().category());
                txn.setSubcategory(match.get().subcategory());
                categorized++;
            }
        }
        log.info("Categorized {}/{} transaction(s)", categorized, transactions.size());
        return categorized;
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

    private Optional<Categorization> matchFallback(String haystack) {
        return fallbackRules.rules().stream()
                .filter(rule -> haystack.contains(rule.keyword()))
                .max(Comparator.comparingInt(rule -> rule.keyword().length()))
                .map(rule -> new Categorization(rule.category(), rule.subcategory()));
    }

    /**
     * Best-effort merchant extraction. UPI narrations follow
     * {@code UPI/<ref>/<payee>/<vpa>/<bank>/...}; for anything else, take the first token that
     * looks like a word rather than a reference number.
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
