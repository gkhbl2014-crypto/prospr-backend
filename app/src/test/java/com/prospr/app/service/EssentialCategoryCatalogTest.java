package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EssentialCategoryCatalogTest {

    private final EssentialCategoryCatalog catalog = new EssentialCategoryCatalog();

    @Test
    void essentialCategoriesAreRecognized() {
        for (String essential : new String[] {
                "RENT", "EMI", "LOAN_PAYMENT", "INSURANCE", "MEDICAL", "GROCERIES", "UTILITIES"
        }) {
            assertThat(catalog.isEssential(essential)).as("category %s should be essential", essential).isTrue();
        }
        assertThat(catalog.isEssential("rent")).isTrue();
    }

    @Test
    void discretionaryAndTransferCategoriesAreNeverEssential() {
        for (String notEssential : new String[] {
                "TAX", "INVESTMENT", "CASH_WITHDRAWAL", "CREDIT_CARD_PAYMENT", "TRANSFER", "SAVINGS",
                "SHOPPING", "ENTERTAINMENT", "DINING", "LUXURY"
        }) {
            assertThat(catalog.isEssential(notEssential)).as("category %s must not be essential", notEssential).isFalse();
        }
    }

    @Test
    void nullCategoryIsNeverEssential() {
        assertThat(catalog.isEssential(null)).isFalse();
    }
}
