package com.prospr.app.service;

/** Emergency Fund coverage states surfaced by GET /api/safety-net. */
public final class EmergencyFundStatus {

    public static final String NO_EMERGENCY_FUND = "NO_EMERGENCY_FUND";
    public static final String CRITICAL = "CRITICAL";
    public static final String BUILDING = "BUILDING";
    public static final String ADEQUATE = "ADEQUATE";
    /** Essential-expense history isn't available yet - never guess a status without it. */
    public static final String UNKNOWN = "UNKNOWN";

    private EmergencyFundStatus() {
    }
}
