package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for transaction classification and the review queue - kept here (not scattered through
 * {@code ReviewQueueService}/{@code PayeePatternService}) so thresholds for "is this material enough
 * to ask the user about" can change without touching code. See application.yml ("classification.*").
 */
@Configuration
@ConfigurationProperties(prefix = "classification")
@Getter
@Setter
public class ClassificationProperties {

    /** A counterparty group must have contributed at least this much (summed absolute amount, INR)
     *  before it's worth surfacing in the review queue - trims insignificant one-off small payments. */
    private double reviewQueueMinTotalAmountInr = 300;

    /** Maximum number of counterparty groups returned by the review queue in one call. */
    private int reviewQueueMaxResults = 20;

    /** Minimum occurrences at the same counterparty before pattern evidence (amount/cadence) is
     *  computed and surfaced - a single payment has no pattern to report. */
    private int patternMinOccurrences = 2;

    /** Amount tolerance (percent of the group's median) within which occurrences are considered
     *  "the same amount" for pattern evidence - mirrors RecurringExpenseProperties' approach, kept
     *  separate since this operates on counterparties, not merchant+category groups. */
    private double patternAmountTolerancePercent = 10.0;
}
