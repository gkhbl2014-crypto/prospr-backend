package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LifestyleCategoryCatalogTest {

    private final LifestyleCategoryCatalog catalog = new LifestyleCategoryCatalog();

    @Test
    void lifestyleCategoriesAreRecognized() {
        assertThat(catalog.isLifestyleCategory("DINING")).isTrue();
        assertThat(catalog.isLifestyleCategory("dining")).isTrue();
        assertThat(catalog.isLifestyleCategory("SHOPPING")).isTrue();
    }

    @Test
    void excludedEssentialCategoriesAreNeverTreatedAsLifestyle() {
        for (String excluded : new String[] {
                "RENT", "EMI", "LOAN_PAYMENT", "INSURANCE", "MEDICAL", "TAX", "TRANSFER",
                "INVESTMENT", "SAVINGS", "CASH_WITHDRAWAL", "CREDIT_CARD_PAYMENT"
        }) {
            assertThat(catalog.isLifestyleCategory(excluded))
                    .as("category %s must not be a lifestyle category", excluded)
                    .isFalse();
            assertThat(catalog.isExcluded(excluded)).isTrue();
        }
    }

    @Test
    void unknownCategoryFallsBackToRawValueAsLabel() {
        assertThat(catalog.label("SOMETHING_NEW")).isEqualTo("SOMETHING_NEW");
        assertThat(catalog.label("DINING")).isEqualTo("Dining");
    }
}
