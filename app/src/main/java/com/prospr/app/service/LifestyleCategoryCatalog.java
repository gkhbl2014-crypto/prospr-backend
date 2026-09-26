package com.prospr.app.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * The fixed set of categories lifestyle-creep analysis looks at, plus the categories explicitly
 * excluded (essential/non-discretionary spend). Kept separate from the analyzer so the category
 * list can be extended without touching detection logic.
 */
@Component
public class LifestyleCategoryCatalog {

    /** A user's own "this is lifestyle creep" tag on a transaction (see TransactionController's tag
     *  endpoint) - treated exactly like any other lifestyle category from here on. */
    public static final String MARKED_LIFESTYLE_CREEP = "MARKED_LIFESTYLE_CREEP";

    private static final Map<String, String> LIFESTYLE_CATEGORY_LABELS = new LinkedHashMap<>();

    static {
        LIFESTYLE_CATEGORY_LABELS.put("DINING", "Dining");
        LIFESTYLE_CATEGORY_LABELS.put("FOOD_DELIVERY", "Food Delivery");
        LIFESTYLE_CATEGORY_LABELS.put("SHOPPING", "Shopping");
        LIFESTYLE_CATEGORY_LABELS.put("ENTERTAINMENT", "Entertainment");
        LIFESTYLE_CATEGORY_LABELS.put("TRAVEL", "Travel");
        LIFESTYLE_CATEGORY_LABELS.put("SUBSCRIPTIONS", "Subscriptions");
        LIFESTYLE_CATEGORY_LABELS.put("PERSONAL_CARE", "Personal Care");
        LIFESTYLE_CATEGORY_LABELS.put("FITNESS", "Fitness");
        LIFESTYLE_CATEGORY_LABELS.put("HOBBIES", "Hobbies");
        LIFESTYLE_CATEGORY_LABELS.put("LUXURY", "Luxury");
        LIFESTYLE_CATEGORY_LABELS.put(MARKED_LIFESTYLE_CREEP, "Lifestyle Creep (Tagged)");
    }

    /** Non-discretionary/essential categories: never treated as lifestyle creep even if categorized. */
    private static final Set<String> EXCLUDED_CATEGORIES = Set.of(
            "RENT", "EMI", "LOAN_PAYMENT", "INSURANCE", "MEDICAL", "TAX", "TRANSFER",
            "INVESTMENT", "SAVINGS", "CASH_WITHDRAWAL", "CREDIT_CARD_PAYMENT",
            "EDUCATION", "TRANSPORTATION", "FUEL", "SALARY_INCOME", "REFUND");

    public Set<String> lifestyleCategories() {
        return LIFESTYLE_CATEGORY_LABELS.keySet();
    }

    public boolean isLifestyleCategory(String category) {
        return category != null && LIFESTYLE_CATEGORY_LABELS.containsKey(category.toUpperCase());
    }

    public boolean isExcluded(String category) {
        return category != null && EXCLUDED_CATEGORIES.contains(category.toUpperCase());
    }

    public String label(String category) {
        if (category == null) {
            return "Uncategorized";
        }
        return LIFESTYLE_CATEGORY_LABELS.getOrDefault(category.toUpperCase(), category);
    }
}
