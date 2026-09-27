package com.prospr.app.service;

import java.util.Set;

/** The fixed set of goal types a member can pick when creating a {@link com.prospr.app.entity.Goal}. */
public final class GoalType {

    public static final String MARRIAGE = "MARRIAGE";
    public static final String HOME = "HOME";
    public static final String CAR = "CAR";
    public static final String EDUCATION = "EDUCATION";
    public static final String TRAVEL = "TRAVEL";
    public static final String EMERGENCY_FUND = "EMERGENCY_FUND";
    public static final String INVESTMENT = "INVESTMENT";
    public static final String CUSTOM = "CUSTOM";

    public static final Set<String> ALL = Set.of(
            MARRIAGE, HOME, CAR, EDUCATION, TRAVEL, EMERGENCY_FUND, INVESTMENT, CUSTOM);

    private GoalType() {
    }
}
