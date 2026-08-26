package com.prospr.app.service;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Keyword rules used only when a transaction's merchant/narration doesn't match anything in the
 * {@code merchant_category} table. Deliberately just a plain list here (not baked into the
 * categorizer or the analyzer) so new keywords can be added without touching either.
 */
@Component
public class CategoryFallbackRules {

    public record Rule(String keyword, String category, String subcategory) {
    }

    private static final List<Rule> RULES = List.of(
            // Food delivery
            new Rule("swiggy", "FOOD_DELIVERY", "Delivery"),
            new Rule("zomato", "FOOD_DELIVERY", "Delivery"),
            // Dining
            new Rule("restaurant", "DINING", null),
            new Rule("cafe", "DINING", null),
            new Rule("starbucks", "DINING", "Coffee"),
            // Shopping
            new Rule("amazon", "SHOPPING", "Online"),
            new Rule("flipkart", "SHOPPING", "Online"),
            new Rule("myntra", "SHOPPING", "Fashion"),
            new Rule("ajio", "SHOPPING", "Fashion"),
            // Entertainment
            new Rule("bookmyshow", "ENTERTAINMENT", "Movies"),
            new Rule("pvr", "ENTERTAINMENT", "Movies"),
            new Rule("inox", "ENTERTAINMENT", "Movies"),
            // Travel
            new Rule("uber", "TRAVEL", "Cab"),
            new Rule("ola", "TRAVEL", "Cab"),
            new Rule("irctc", "TRAVEL", "Rail"),
            new Rule("makemytrip", "TRAVEL", "Booking"),
            new Rule("goibibo", "TRAVEL", "Booking"),
            new Rule("indigo", "TRAVEL", "Flight"),
            // Subscriptions
            new Rule("netflix", "SUBSCRIPTIONS", "Streaming"),
            new Rule("spotify", "SUBSCRIPTIONS", "Streaming"),
            new Rule("hotstar", "SUBSCRIPTIONS", "Streaming"),
            new Rule("primevideo", "SUBSCRIPTIONS", "Streaming"),
            // Personal care
            new Rule("mamaearth", "PERSONAL_CARE", null),
            new Rule("nykaa", "PERSONAL_CARE", null),
            new Rule("salon", "PERSONAL_CARE", "Salon"),
            // Fitness
            new Rule("cultfit", "FITNESS", "Gym"),
            new Rule("cult.fit", "FITNESS", "Gym"),
            new Rule("gym", "FITNESS", "Gym"),
            // Excluded/essential categories - still worth categorizing correctly so they are
            // reliably kept OUT of lifestyle sums rather than left as uncategorized noise.
            new Rule("rent", "RENT", null),
            new Rule("emi", "EMI", null),
            new Rule("loan", "LOAN_PAYMENT", null),
            new Rule("premium", "INSURANCE", null),
            new Rule("insurance", "INSURANCE", null),
            new Rule("hospital", "MEDICAL", null),
            new Rule("pharmacy", "MEDICAL", null),
            new Rule("apollo", "MEDICAL", null),
            new Rule("incometax", "TAX", null),
            new Rule("gst", "TAX", null),
            new Rule("mutualfund", "INVESTMENT", null),
            new Rule("zerodha", "INVESTMENT", null),
            new Rule("groww", "INVESTMENT", null),
            new Rule("sip", "INVESTMENT", null),
            new Rule("atmwdl", "CASH_WITHDRAWAL", null),
            new Rule("cashwithdrawal", "CASH_WITHDRAWAL", null),
            new Rule("creditcardbillpay", "CREDIT_CARD_PAYMENT", null),
            new Rule("ccpayment", "CREDIT_CARD_PAYMENT", null));

    public List<Rule> rules() {
        return RULES;
    }
}
