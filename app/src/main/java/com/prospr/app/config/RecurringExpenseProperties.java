package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for recurring-expense detection - see application.yml ("recurring-expense.*") for the
 * actual values, mirroring {@link LifestyleProperties}'s externalized-properties pattern.
 */
@Configuration
@ConfigurationProperties(prefix = "recurring-expense")
@Getter
@Setter
public class RecurringExpenseProperties {

    /** Minimum number of same-merchant occurrences before a pattern is even considered. */
    private int minOccurrences = 3;

    /** Amount is "regular" if every occurrence is within this percent of the median amount. */
    private double amountTolerancePercent = 10.0;

    /** ...or within this absolute INR floor, whichever is looser (protects small recurring amounts
     *  where a 10% band would be unrealistically tight, e.g. a Rs99 subscription). */
    private double amountToleranceFloorInr = 50.0;

    /** Day-of-month gap tolerance for a MONTHLY cadence. */
    private int monthlyIntervalToleranceDays = 4;

    /** Interval tolerance around a ~90-day gap for a QUARTERLY cadence. */
    private int quarterlyIntervalToleranceDays = 7;

    /** Interval tolerance around a ~365-day gap for an ANNUAL cadence. */
    private int annualIntervalToleranceDays = 14;

    /** A pattern is marked LAPSED once this many cadence-lengths have passed since the last occurrence. */
    private double activeLapseMultiplier = 1.5;

    /** HIGH confidence requires at least this many occurrences (on top of the other HIGH conditions). */
    private int highConfidenceMinOccurrences = 4;

    /** HIGH confidence requires amounts within this tighter percent band (on top of MONTHLY cadence). */
    private double highConfidenceAmountTolerancePercent = 5.0;
}
