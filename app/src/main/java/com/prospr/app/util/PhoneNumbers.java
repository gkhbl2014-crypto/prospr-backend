package com.prospr.app.util;

import java.util.regex.Pattern;

/** Canonical form for Indian mobile numbers, so "+91 98100 12345", "09810012345" and "9810012345"
 *  are all recognised as the same account. */
public final class PhoneNumbers {

    private static final Pattern INDIAN_MOBILE = Pattern.compile("^[6-9]\\d{9}$");

    private PhoneNumbers() {
    }

    /** @return "+91" followed by the 10-digit number
     *  @throws IllegalArgumentException if the input isn't a valid Indian mobile number */
    public static String normalizeIndian(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!INDIAN_MOBILE.matcher(digits).matches()) {
            throw new IllegalArgumentException("Enter a valid 10-digit Indian mobile number");
        }
        return "+91" + digits;
    }
}
