package com.prospr.app.service;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The category set treated as "essential living expense" for Safety Net's emergency-fund and
 * life-insurance-target calculations. Deliberately narrower than {@link LifestyleCategoryCatalog}'s
 * excluded set: TAX, INVESTMENT, CASH_WITHDRAWAL, CREDIT_CARD_PAYMENT, TRANSFER and SAVINGS are all
 * "not lifestyle creep" for Lifestyle's purposes, but they're financial transfers rather than living
 * expenses, so they're intentionally left out here.
 *
 * EDUCATION, TRANSPORTATION and FUEL were previously a documented gap (no keyword signal existed to
 * categorize them) - {@link CategoryFallbackRules} now has rules for all three, so they're included.
 */
@Component
public class EssentialCategoryCatalog {

    private static final Set<String> ESSENTIAL_CATEGORIES = Set.of(
            "RENT", "EMI", "LOAN_PAYMENT", "INSURANCE", "MEDICAL", "GROCERIES", "UTILITIES",
            "EDUCATION", "TRANSPORTATION", "FUEL");

    public Set<String> essentialCategories() {
        return ESSENTIAL_CATEGORIES;
    }

    public boolean isEssential(String category) {
        return category != null && ESSENTIAL_CATEGORIES.contains(category.toUpperCase());
    }
}
