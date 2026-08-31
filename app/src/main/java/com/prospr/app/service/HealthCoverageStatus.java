package com.prospr.app.service;

/**
 * Health Insurance coverage states surfaced by GET /api/safety-net. DETECTED and ADEQUATE are valid
 * values but are never auto-assigned in v1 - there's no per-member benchmark (age, city, dependents)
 * to judge sufficiency against, so any coverage found always surfaces as REVIEW_RECOMMENDED rather
 * than a fabricated threshold like "sum insured >= 10 lakh".
 */
public final class HealthCoverageStatus {

    public static final String NO_COVER = "NO_COVER";
    public static final String DETECTED = "DETECTED";
    public static final String REVIEW_RECOMMENDED = "REVIEW_RECOMMENDED";
    public static final String ADEQUATE = "ADEQUATE";

    private HealthCoverageStatus() {
    }
}
