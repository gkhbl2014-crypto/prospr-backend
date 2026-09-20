package com.prospr.app.service;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Keyword rules used only when a transaction's merchant/narration doesn't match anything in the
 * {@code merchant_category} table. Deliberately just a plain list here (not baked into the
 * categorizer or the analyzer) so new keywords can be added without touching either.
 *
 * <p>{@code specific} marks whether the keyword unambiguously identifies a single merchant/purpose
 * (a brand name like "swiggy", or a curated financial-purpose term like "rent"/"emi" that Safety Net's
 * essential-expense calculation already depends on) versus a generic dictionary word that could
 * plausibly appear inside unrelated narrations ("cafe", "gym"). {@link TransactionCategorizationService}
 * only assigns a category for {@code specific=false} matches when nothing more specific matched too,
 * and marks the result LOW confidence rather than guessing outright wrong.
 */
@Component
public class CategoryFallbackRules {

    public record Rule(String keyword, String category, String subcategory, boolean specific) {
        public Rule(String keyword, String category, String subcategory) {
            this(keyword, category, subcategory, true);
        }
    }

    private static final List<Rule> RULES = List.of(
            // Food delivery
            new Rule("swiggy", "FOOD_DELIVERY", "Delivery"),
            new Rule("zomato", "FOOD_DELIVERY", "Delivery"),
            // Dining - generic words, not brand names, so left LOW-confidence/unclassified unless a
            // more specific rule (e.g. "starbucks") also matches the same narration.
            new Rule("restaurant", "DINING", null, false),
            new Rule("cafe", "DINING", null, false),
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
            // Travel (flights/hotels/rail/vacation booking) - distinct from everyday commuting, which
            // is now its own TRANSPORTATION category below.
            new Rule("irctc", "TRAVEL", "Rail"),
            new Rule("makemytrip", "TRAVEL", "Booking"),
            new Rule("goibibo", "TRAVEL", "Booking"),
            new Rule("indigo", "TRAVEL", "Flight"),
            // Transportation (everyday commute) - uber/ola were previously bucketed under TRAVEL/Cab;
            // moved here since ride-hailing for daily commute isn't a vacation/travel expense.
            new Rule("uber", "TRANSPORTATION", "Cab"),
            new Rule("ola", "TRANSPORTATION", "Cab"),
            new Rule("rapido", "TRANSPORTATION", "Bike Taxi"),
            new Rule("metro", "TRANSPORTATION", "Metro"),
            // Fuel
            new Rule("iocl", "FUEL", null),
            new Rule("hpcl", "FUEL", null),
            new Rule("bpcl", "FUEL", null),
            new Rule("indianoil", "FUEL", null),
            new Rule("petrolpump", "FUEL", null),
            new Rule("petrolbunk", "FUEL", null),
            // Subscriptions
            new Rule("netflix", "SUBSCRIPTIONS", "Streaming"),
            new Rule("spotify", "SUBSCRIPTIONS", "Streaming"),
            new Rule("hotstar", "SUBSCRIPTIONS", "Streaming"),
            new Rule("primevideo", "SUBSCRIPTIONS", "Streaming"),
            // Personal care
            new Rule("mamaearth", "PERSONAL_CARE", null),
            new Rule("nykaa", "PERSONAL_CARE", null),
            new Rule("salon", "PERSONAL_CARE", "Salon", false),
            // Fitness
            new Rule("cultfit", "FITNESS", "Gym"),
            new Rule("cult.fit", "FITNESS", "Gym"),
            new Rule("gym", "FITNESS", "Gym", false),
            // Education - previously a documented gap with no keyword signal at all.
            new Rule("tuition", "EDUCATION", null),
            new Rule("byjus", "EDUCATION", null),
            new Rule("unacademy", "EDUCATION", null),
            new Rule("vedantu", "EDUCATION", null),
            new Rule("udemy", "EDUCATION", null),
            new Rule("coursera", "EDUCATION", null),
            new Rule("schoolfee", "EDUCATION", null),
            new Rule("university", "EDUCATION", null),
            // Groceries / utilities - essential for Safety Net's emergency-fund calculation.
            new Rule("bigbasket", "GROCERIES", null),
            new Rule("dmart", "GROCERIES", null),
            new Rule("grofers", "GROCERIES", null),
            new Rule("jiomart", "GROCERIES", null),
            new Rule("grocery", "GROCERIES", null),
            new Rule("electricity", "UTILITIES", null),
            new Rule("broadband", "UTILITIES", null),
            new Rule("jiofiber", "UTILITIES", null),
            new Rule("gasbill", "UTILITIES", null),
            new Rule("waterbill", "UTILITIES", null),
            new Rule("vodafoneidea", "UTILITIES", null),
            new Rule("airtel", "UTILITIES", null),
            new Rule("reliancejio", "UTILITIES", null),
            new Rule("bsnl", "UTILITIES", null),
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
