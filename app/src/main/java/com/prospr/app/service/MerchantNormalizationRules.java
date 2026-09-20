package com.prospr.app.service;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Keyword -> canonical merchant name rules used when nothing in the {@code merchant_alias} table
 * matches. Groups narration variants of the same real-world merchant ("SWIGGY", "SWIGGY INSTAMART",
 * "SWIGGY GENIE") under one display name ("Swiggy") so spend-by-merchant views aren't fragmented.
 * Longest-keyword-wins tie-break, matching {@link CategoryFallbackRules}' convention, so a more
 * specific variant (e.g. "swiggy instamart") can still resolve to the same canonical name as the
 * shorter brand keyword without needing its own separate entry unless the display name should differ.
 */
@Component
public class MerchantNormalizationRules {

    public record Rule(String keyword, String canonicalName) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("swiggy", "Swiggy"),
            new Rule("zomato", "Zomato"),
            new Rule("zepto", "Zepto"),
            new Rule("blinkit", "Blinkit"),
            new Rule("amazon", "Amazon"),
            new Rule("flipkart", "Flipkart"),
            new Rule("myntra", "Myntra"),
            new Rule("ajio", "Ajio"),
            new Rule("nykaa", "Nykaa"),
            new Rule("bigbasket", "BigBasket"),
            new Rule("dmart", "DMart"),
            new Rule("grofers", "Grofers"),
            new Rule("jiomart", "JioMart"),
            new Rule("uber", "Uber"),
            new Rule("ola", "Ola"),
            new Rule("rapido", "Rapido"),
            new Rule("irctc", "IRCTC"),
            new Rule("makemytrip", "MakeMyTrip"),
            new Rule("goibibo", "Goibibo"),
            new Rule("indigo", "IndiGo"),
            new Rule("netflix", "Netflix"),
            new Rule("spotify", "Spotify"),
            new Rule("hotstar", "Disney+ Hotstar"),
            new Rule("primevideo", "Prime Video"),
            new Rule("bookmyshow", "BookMyShow"),
            new Rule("pvr", "PVR Cinemas"),
            new Rule("inox", "INOX"),
            new Rule("airtel", "Airtel"),
            new Rule("jiofiber", "Jio"),
            new Rule("reliancejio", "Jio"),
            new Rule("vodafoneidea", "Vi"),
            new Rule("zerodha", "Zerodha"),
            new Rule("groww", "Groww"),
            new Rule("cultfit", "Cult.fit"),
            new Rule("cult.fit", "Cult.fit"),
            new Rule("mamaearth", "Mamaearth"),
            new Rule("apollo", "Apollo"),
            new Rule("byjus", "BYJU'S"),
            new Rule("unacademy", "Unacademy"),
            new Rule("vedantu", "Vedantu"));

    public List<Rule> rules() {
        return RULES;
    }
}
