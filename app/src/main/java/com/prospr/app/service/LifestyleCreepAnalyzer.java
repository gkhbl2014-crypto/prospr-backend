package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.prospr.app.config.LifestyleProperties;

/**
 * Pure comparison engine: baseline amount in, current amount out, severity/insight decision out.
 * Deliberately has no dependency on Setu, JPA, or any entity - it only ever sees primitives, so it
 * can be unit tested directly and later reused for family-level analysis without change.
 */
@Component
public class LifestyleCreepAnalyzer {

    public enum Severity {
        NORMAL, LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * @param increasePercentage null when the baseline is zero/negative - see {@link #analyze}.
     */
    public record AnalysisResult(Severity severity, boolean insightWorthy, BigDecimal differenceAmount,
                                  Double increasePercentage) {
    }

    private final LifestyleProperties properties;

    public LifestyleCreepAnalyzer(LifestyleProperties properties) {
        this.properties = properties;
    }

    /**
     * Compares currentAmount against baselineAmount for one category/period.
     *
     * A zero (or negative, which should never happen but is guarded anyway) baseline would make
     * the percentage-increase undefined, so it's never divided by - the result is NORMAL/not
     * insight-worthy. A true "you started spending in a brand-new category" insight is a separate
     * insight type (NEW_SPENDING_CATEGORY) intentionally deferred past V1.
     */
    public AnalysisResult analyze(BigDecimal baselineAmount, BigDecimal currentAmount) {
        BigDecimal baseline = baselineAmount == null ? BigDecimal.ZERO : baselineAmount;
        BigDecimal current = currentAmount == null ? BigDecimal.ZERO : currentAmount;
        BigDecimal difference = current.subtract(baseline);

        if (baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return new AnalysisResult(Severity.NORMAL, false, difference, null);
        }

        double increasePercentage = difference
                .divide(baseline, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        Severity severity = classify(increasePercentage);

        // An insight is only worth surfacing when BOTH the percentage and the absolute rupee
        // difference clear their thresholds - otherwise a tiny amount doubling (e.g. Rs 100 -> 200)
        // would generate noise. Both thresholds are configurable (see LifestyleProperties /
        // application.yml "lifestyle.*").
        boolean insightWorthy = increasePercentage >= properties.getMinIncreasePercentForInsight()
                && difference.compareTo(BigDecimal.valueOf(properties.getMinAbsoluteDifferenceInr())) >= 0;

        return new AnalysisResult(severity, insightWorthy, difference, increasePercentage);
    }

    private Severity classify(double increasePercentage) {
        if (increasePercentage < properties.getLowThresholdPercent()) {
            return Severity.NORMAL;
        }
        if (increasePercentage < properties.getMediumThresholdPercent()) {
            return Severity.LOW;
        }
        if (increasePercentage < properties.getHighThresholdPercent()) {
            return Severity.MEDIUM;
        }
        if (increasePercentage <= properties.getCriticalThresholdPercent()) {
            return Severity.HIGH;
        }
        return Severity.CRITICAL;
    }
}
