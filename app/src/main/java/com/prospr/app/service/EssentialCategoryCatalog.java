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
 * GROCERIES and UTILITIES are included even though EDUCATION and ESSENTIAL_TRANSPORT (also named in
 * the original Safety Net spec) are not - there's no reliable keyword signal yet to categorize
 * education or transport spend, so those two are a documented gap rather than a silent omission.
 */
@Component
public class EssentialCategoryCatalog {

    private static final Set<String> ESSENTIAL_CATEGORIES = Set.of(
            "RENT", "EMI", "LOAN_PAYMENT", "INSURANCE", "MEDICAL", "GROCERIES", "UTILITIES");

    public Set<String> essentialCategories() {
        return ESSENTIAL_CATEGORIES;
    }

    public boolean isEssential(String category) {
        return category != null && ESSENTIAL_CATEGORIES.contains(category.toUpperCase());
    }
}
