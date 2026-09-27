package com.prospr.app.service;

import java.util.Set;

/**
 * The fixed set of answers to "how do you usually use payments to X" - one member's own learned
 * classification for a counterparty, stored on {@link com.prospr.app.entity.MemberTransactionLabel}.
 */
public final class PayeePurpose {

    public static final String PERSONAL_TRANSFER = "PERSONAL_TRANSFER";
    public static final String SHARED_EXPENSE = "SHARED_EXPENSE";
    public static final String REPAYMENT = "REPAYMENT";
    public static final String RENT = "RENT";
    public static final String HOUSEHOLD = "HOUSEHOLD";
    public static final String GIFT = "GIFT";
    public static final String INVESTMENT = "INVESTMENT";
    public static final String OTHER = "OTHER";

    public static final Set<String> ALL = Set.of(
            PERSONAL_TRANSFER, SHARED_EXPENSE, REPAYMENT, RENT, HOUSEHOLD, GIFT, INVESTMENT, OTHER);

    private PayeePurpose() {
    }
}
