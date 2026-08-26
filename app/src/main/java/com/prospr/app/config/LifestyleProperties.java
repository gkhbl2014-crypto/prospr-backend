package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for the Lifestyle Creep feature. Keep thresholds here (not scattered through the
 * analyzer/services) so they can be changed without touching code - see application.yml
 * ("lifestyle.*") for the actual values, including the INR absolute-difference floor.
 */
@Configuration
@ConfigurationProperties(prefix = "lifestyle")
@Getter
@Setter
public class LifestyleProperties {

    /** Number of previous completed calendar months used to build the baseline. */
    private int baselineMonths = 3;

    /** Severity bucket boundaries, in percent increase over baseline. */
    private double lowThresholdPercent = 15;
    private double mediumThresholdPercent = 30;
    private double highThresholdPercent = 50;
    private double criticalThresholdPercent = 75;

    /** An insight is only generated when both this percentage... */
    private double minIncreasePercentForInsight = 30;

    /** ...AND this absolute INR difference are met, so a tiny amount doubling doesn't count. */
    private double minAbsoluteDifferenceInr = 500;
}
