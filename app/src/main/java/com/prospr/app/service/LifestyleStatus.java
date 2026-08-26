package com.prospr.app.service;

/** The lifestyle-analysis pipeline states surfaced by GET /api/lifestyle/status. */
public final class LifestyleStatus {

    public static final String NOT_ENABLED = "NOT_ENABLED";
    public static final String CONSENT_PENDING = "CONSENT_PENDING";
    public static final String FETCHING_HISTORY = "FETCHING_HISTORY";
    public static final String CATEGORIZING = "CATEGORIZING";
    public static final String BUILDING_BASELINE = "BUILDING_BASELINE";
    public static final String READY = "READY";
    public static final String INSUFFICIENT_HISTORY = "INSUFFICIENT_HISTORY";
    public static final String ERROR = "ERROR";

    private LifestyleStatus() {
    }
}
