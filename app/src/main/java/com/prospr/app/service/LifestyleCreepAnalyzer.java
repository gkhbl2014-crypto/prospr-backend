package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Pure diff/percentage calculator: baseline in, current in, {@code differenceAmount}/{@code
 * increasePercentage} out. No thresholds and no classification live here - see {@link
 * LifestyleTrendEvaluator} for turning a series of these results (one per month) into an
 * elevated/not-elevated call and the overall STABLE/WATCH/INCREASING state.
 */
@Component
public class LifestyleCreepAnalyzer {

    /** @param increasePercentage null when the baseline is zero/negative - never divided by. */
    public record AnalysisResult(BigDecimal differenceAmount, Double increasePercentage) {
    }

    public AnalysisResult analyze(BigDecimal baselineAmount, BigDecimal currentAmount) {
        BigDecimal baseline = baselineAmount == null ? BigDecimal.ZERO : baselineAmount;
        BigDecimal current = currentAmount == null ? BigDecimal.ZERO : currentAmount;
        BigDecimal difference = current.subtract(baseline);

        if (baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return new AnalysisResult(difference, null);
        }

        double increasePercentage = difference
                .divide(baseline, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        return new AnalysisResult(difference, increasePercentage);
    }
}
