package com.prospr.app.service;

/** Life Insurance coverage states surfaced by GET /api/safety-net. */
public final class LifeCoverageStatus {

    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String NO_COVER = "NO_COVER";
    public static final String GAP_DETECTED = "GAP_DETECTED";
    public static final String ADEQUATE = "ADEQUATE";
    /** Required inputs (income replacement years, essential-expense history) aren't available. */
    public static final String REVIEW_RECOMMENDED = "REVIEW_RECOMMENDED";

    private LifeCoverageStatus() {
    }
}
