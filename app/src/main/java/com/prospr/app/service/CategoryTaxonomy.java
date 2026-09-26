package com.prospr.app.service;

import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Maps the fine-grained category codes {@link TransactionCategorizationService}/{@link CategoryFallbackRules}
 * actually assign (FOOD_DELIVERY, DINING, MEDICAL, ...) to the 20 user-facing top-level names
 * requested for the financial analysis engine. Kept as a pure display layer, not a schema change:
 * the underlying {@code Transaction.category} column keeps its existing fine-grained codes so
 * nothing downstream (Lifestyle/Safety Net, both keyed on those codes) needs to change.
 */
@Component
public class CategoryTaxonomy {

    public static final String FOOD_AND_DINING = "Food & Dining";
    public static final String GROCERIES = "Groceries";
    public static final String SHOPPING = "Shopping";
    public static final String TRANSPORTATION = "Transportation";
    public static final String FUEL = "Fuel";
    public static final String UTILITIES = "Utilities";
    public static final String RENT = "Rent";
    public static final String HEALTHCARE = "Healthcare";
    public static final String EDUCATION = "Education";
    public static final String ENTERTAINMENT = "Entertainment";
    public static final String TRAVEL = "Travel";
    public static final String SUBSCRIPTIONS = "Subscriptions";
    public static final String INSURANCE = "Insurance";
    public static final String INVESTMENTS = "Investments";
    public static final String LOAN_EMI = "Loan/EMI";
    public static final String ATM_CASH_WITHDRAWAL = "ATM/Cash Withdrawal";
    public static final String TRANSFERS = "Transfers";
    public static final String SALARY_INCOME = "Salary/Income";
    public static final String OTHER = "Other";
    public static final String UNCLASSIFIED = "Unclassified";
    public static final String ESSENTIAL_TAGGED = "Essential (Tagged)";
    public static final String LIFESTYLE_CREEP_TAGGED = "Lifestyle Creep (Tagged)";

    private static final Map<String, String> CODE_TO_TOP_LEVEL = Map.ofEntries(
            Map.entry("FOOD_DELIVERY", FOOD_AND_DINING),
            Map.entry("DINING", FOOD_AND_DINING),
            Map.entry("GROCERIES", GROCERIES),
            Map.entry("SHOPPING", SHOPPING),
            Map.entry("TRANSPORTATION", TRANSPORTATION),
            Map.entry("FUEL", FUEL),
            Map.entry("UTILITIES", UTILITIES),
            Map.entry("RENT", RENT),
            Map.entry("MEDICAL", HEALTHCARE),
            Map.entry("EDUCATION", EDUCATION),
            Map.entry("ENTERTAINMENT", ENTERTAINMENT),
            Map.entry("TRAVEL", TRAVEL),
            Map.entry("SUBSCRIPTIONS", SUBSCRIPTIONS),
            Map.entry("INSURANCE", INSURANCE),
            Map.entry("INVESTMENT", INVESTMENTS),
            Map.entry("EMI", LOAN_EMI),
            Map.entry("LOAN_PAYMENT", LOAN_EMI),
            Map.entry("CASH_WITHDRAWAL", ATM_CASH_WITHDRAWAL),
            Map.entry("CREDIT_CARD_PAYMENT", TRANSFERS),
            Map.entry("TRANSFER", TRANSFERS),
            Map.entry("SALARY_INCOME", SALARY_INCOME),
            Map.entry("TAX", OTHER),
            Map.entry("PERSONAL_CARE", OTHER),
            Map.entry("FITNESS", OTHER),
            Map.entry("HOBBIES", OTHER),
            Map.entry("LUXURY", OTHER),
            Map.entry("SAVINGS", OTHER),
            Map.entry(EssentialCategoryCatalog.MARKED_ESSENTIAL, ESSENTIAL_TAGGED),
            Map.entry(LifestyleCategoryCatalog.MARKED_LIFESTYLE_CREEP, LIFESTYLE_CREEP_TAGGED));

    /** Never returns null - an unrecognized or absent category maps to "Unclassified". */
    public String topLevelName(String category) {
        if (category == null) {
            return UNCLASSIFIED;
        }
        return CODE_TO_TOP_LEVEL.getOrDefault(category.toUpperCase(Locale.ROOT), OTHER);
    }
}
