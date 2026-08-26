package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.service.LifestyleCreepAnalyzer.AnalysisResult;
import com.prospr.app.service.LifestyleCreepAnalyzer.Severity;

/**
 * LifestyleProperties is constructed directly (not via Spring context) since it's a plain POJO
 * with the same defaults as application.yml's "lifestyle.*" block (15/30/50/75/30/500).
 */
class LifestyleCreepAnalyzerTest {

    private final LifestyleCreepAnalyzer analyzer = new LifestyleCreepAnalyzer(new LifestyleProperties());

    @Test
    void normalSpendingDoesNotTriggerAnInsight() {
        AnalysisResult result = analyzer.analyze(new BigDecimal("4500"), new BigDecimal("4800"));

        assertThat(result.severity()).isEqualTo(Severity.NORMAL);
        assertThat(result.insightWorthy()).isFalse();
    }

    @Test
    void sixtyPercentIncreaseIsHighSeverityAndInsightWorthy() {
        AnalysisResult result = analyzer.analyze(new BigDecimal("4500"), new BigDecimal("7200"));

        assertThat(result.differenceAmount()).isEqualByComparingTo("2700");
        assertThat(result.increasePercentage()).isEqualTo(60.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.severity()).isEqualTo(Severity.HIGH);
        assertThat(result.insightWorthy()).isTrue();
    }

    @Test
    void criticalIncreaseAboveSeventyFivePercent() {
        AnalysisResult result = analyzer.analyze(new BigDecimal("3000"), new BigDecimal("6000"));

        assertThat(result.increasePercentage()).isEqualTo(100.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(result.insightWorthy()).isTrue();
    }

    @Test
    void smallAbsoluteAmountBelowThresholdIsNotInsightWorthyEvenIfPercentageDoubles() {
        // 100 -> 200 is a 100% increase, comfortably over the 30% gate, but the Rs100 absolute
        // difference is far below the configured Rs500 floor.
        AnalysisResult result = analyzer.analyze(new BigDecimal("100"), new BigDecimal("200"));

        assertThat(result.increasePercentage()).isEqualTo(100.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.insightWorthy()).isFalse();
    }

    @Test
    void zeroBaselineDoesNotDivideByZero() {
        AnalysisResult result = analyzer.analyze(BigDecimal.ZERO, new BigDecimal("500"));

        assertThat(result.increasePercentage()).isNull();
        assertThat(result.severity()).isEqualTo(Severity.NORMAL);
        assertThat(result.insightWorthy()).isFalse();
    }

    @Test
    void mediumSeverityBetweenThirtyAndFiftyPercent() {
        // 35% increase, and a Rs700 absolute difference clears the Rs500 floor.
        AnalysisResult result = analyzer.analyze(new BigDecimal("2000"), new BigDecimal("2700"));

        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
        assertThat(result.insightWorthy()).isTrue();
    }

    @Test
    void lowSeverityBetweenFifteenAndThirtyPercentIsNeverInsightWorthy() {
        // Below the 30% insight gate even though it's classified LOW, not NORMAL.
        AnalysisResult result = analyzer.analyze(new BigDecimal("1000"), new BigDecimal("1200"));

        assertThat(result.severity()).isEqualTo(Severity.LOW);
        assertThat(result.insightWorthy()).isFalse();
    }
}
