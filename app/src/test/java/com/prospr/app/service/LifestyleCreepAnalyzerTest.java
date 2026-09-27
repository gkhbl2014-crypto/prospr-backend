package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.prospr.app.service.LifestyleCreepAnalyzer.AnalysisResult;

/** Pure diff/percentage math only now - see LifestyleTrendEvaluatorTest for the elevated/streak/state
 *  decisions that used to live in this class as Severity/insightWorthy. */
class LifestyleCreepAnalyzerTest {

    private final LifestyleCreepAnalyzer analyzer = new LifestyleCreepAnalyzer();

    @Test
    void computesDifferenceAndPercentageForAnIncrease() {
        AnalysisResult result = analyzer.analyze(new BigDecimal("4500"), new BigDecimal("7200"));

        assertThat(result.differenceAmount()).isEqualByComparingTo("2700");
        assertThat(result.increasePercentage()).isEqualTo(60.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void computesANegativeDifferenceWhenCurrentIsBelowBaseline() {
        AnalysisResult result = analyzer.analyze(new BigDecimal("20100"), new BigDecimal("19800"));

        assertThat(result.differenceAmount()).isEqualByComparingTo("-300");
        assertThat(result.increasePercentage()).isEqualTo(-1.49, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void zeroBaselineDoesNotDivideByZero() {
        AnalysisResult result = analyzer.analyze(BigDecimal.ZERO, new BigDecimal("500"));

        assertThat(result.differenceAmount()).isEqualByComparingTo("500");
        assertThat(result.increasePercentage()).isNull();
    }

    @Test
    void nullInputsAreTreatedAsZero() {
        AnalysisResult result = analyzer.analyze(null, null);

        assertThat(result.differenceAmount()).isEqualByComparingTo("0");
        assertThat(result.increasePercentage()).isNull();
    }
}
