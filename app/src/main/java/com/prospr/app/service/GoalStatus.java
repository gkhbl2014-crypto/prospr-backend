package com.prospr.app.service;

/** The three states {@link GoalCalculator} can resolve a goal to - deliberately simple, no numeric score. */
public final class GoalStatus {

    public static final String ON_TRACK = "ON_TRACK";
    public static final String NEEDS_ATTENTION = "NEEDS_ATTENTION";
    public static final String COMPLETED = "COMPLETED";

    private GoalStatus() {
    }
}
