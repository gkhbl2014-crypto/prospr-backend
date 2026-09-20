package com.prospr.app.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionEffectiveCategoryTest {

    @Test
    void fallsBackToSystemCategoryWhenNoUserOverride() {
        Transaction txn = Transaction.builder().category("SHOPPING").build();

        assertThat(txn.getEffectiveCategory()).isEqualTo("SHOPPING");
    }

    @Test
    void userOverrideTakesPrecedenceOverSystemCategory() {
        Transaction txn = Transaction.builder().category("SHOPPING").userCategoryOverride("RENT").build();

        assertThat(txn.getEffectiveCategory()).isEqualTo("RENT");
    }

    @Test
    void nullWhenNeitherIsSet() {
        Transaction txn = Transaction.builder().build();

        assertThat(txn.getEffectiveCategory()).isNull();
    }
}
