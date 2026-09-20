package com.prospr.app.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.MerchantAlias;
import com.prospr.app.repository.MerchantAliasRepository;

/**
 * Groups narration variants of the same real-world merchant ("SWIGGY", "SWIGGY INSTAMART",
 * "SWIGGY UPI") under one canonical display name ("Swiggy"). Same two-tier lookup shape as
 * {@link TransactionCategorizationService}: a DB-backed table first, then a hardcoded fallback list,
 * longest-keyword-wins on ties.
 */
@Service
public class MerchantNormalizationService {

    private final MerchantAliasRepository merchantAliasRepository;
    private final MerchantNormalizationRules fallbackRules;

    public MerchantNormalizationService(MerchantAliasRepository merchantAliasRepository,
                                         MerchantNormalizationRules fallbackRules) {
        this.merchantAliasRepository = merchantAliasRepository;
        this.fallbackRules = fallbackRules;
    }

    /**
     * Loads the DB-backed alias table once. Callers processing a batch of transactions must call
     * this exactly once and pass the result to every {@link #normalize} call for that batch -
     * calling it per-transaction turns a small, rarely-changing table into an N+1 query storm (a
     * real bug caught in this exact spot: a 6-month history re-querying merchant_alias once per
     * transaction made "recalculate" visibly slow).
     */
    public List<MerchantAlias> loadKnownAliases() {
        return merchantAliasRepository.findAll();
    }

    /** Returns the canonical merchant name for this narration, if any rule recognizes it. */
    public Optional<String> normalize(String narration, List<MerchantAlias> knownAliases) {
        if (narration == null || narration.isBlank()) {
            return Optional.empty();
        }
        String haystack = narration.toLowerCase(Locale.ROOT);

        Optional<String> knownMatch = knownAliases.stream()
                .filter(alias -> alias.getRawKeyword() != null
                        && haystack.contains(alias.getRawKeyword().toLowerCase(Locale.ROOT)))
                .max(Comparator.comparingInt(alias -> alias.getRawKeyword().length()))
                .map(MerchantAlias::getCanonicalName);
        if (knownMatch.isPresent()) {
            return knownMatch;
        }

        return fallbackRules.rules().stream()
                .filter(rule -> haystack.contains(rule.keyword()))
                .max(Comparator.comparingInt(rule -> rule.keyword().length()))
                .map(MerchantNormalizationRules.Rule::canonicalName);
    }
}
