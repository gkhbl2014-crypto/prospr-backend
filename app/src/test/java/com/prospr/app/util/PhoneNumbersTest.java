package com.prospr.app.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void normalizesEveryCommonWayOfTypingTheSameNumber() {
        assertThat(PhoneNumbers.normalizeIndian("9810012345")).isEqualTo("+919810012345");
        assertThat(PhoneNumbers.normalizeIndian("+91 98100 12345")).isEqualTo("+919810012345");
        assertThat(PhoneNumbers.normalizeIndian("919810012345")).isEqualTo("+919810012345");
        assertThat(PhoneNumbers.normalizeIndian("09810012345")).isEqualTo("+919810012345");
        assertThat(PhoneNumbers.normalizeIndian("98100-12345")).isEqualTo("+919810012345");
    }

    @Test
    void rejectsNumbersThatAreNotIndianMobiles() {
        assertThatThrownBy(() -> PhoneNumbers.normalizeIndian("12345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalizeIndian("5810012345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalizeIndian("+1 415 555 0100")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalizeIndian(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
