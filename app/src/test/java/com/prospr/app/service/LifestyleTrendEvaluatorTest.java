package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.prospr.app.config.LifestyleProperties;
import com.prospr.app.service.LifestyleCreepAnalyzer.AnalysisResult;
import com.prospr.app.service.LifestyleTrendEvaluator.State;
import com.prospr.app.service.LifestyleTrendEvaluator.TrendResult;

/** LifestyleProperties is constructed directly (plain POJO, same defaults as application.yml's
 *  "lifestyle.*" block: low-threshold 15%, min-absolute-difference Rs500). */
class LifestyleTrendEvaluatorTest {

    private final LifestyleTrendEvaluator evaluator = new LifestyleTrendEvaluator(new LifestyleProperties());
    private final LifestyleCreepAnalyzer analyzer = new LifestyleCreepAnalyzer();

    private boolean elevatedFor(String baseline, String current) {
        return evaluator.isElevated(analyzer.analyze(new BigDecimal(baseline), new BigDecimal(current)));
    }

    @Test
    void oneTimeSpikeNeverReachesIncreasingBecauseTheStreakResets() {
        // Rs20K -> Rs35K -> Rs21K: only the middle month is elevated vs a ~Rs20K baseline - the streak
        // resets to zero the moment spending drops back, so this must never look "sustained".
        String baseline = "20000";
        boolean month3Elevated = elevatedFor(baseline, "21000");
        boolean month2Elevated = elevatedFor(baseline, "35000");

        TrendResult monthAfterSpike = evaluator.evaluate(List.of(month3Elevated, month2Elevated));
        assertThat(monthAfterSpike.state()).isEqualTo(State.STABLE);
        assertThat(monthAfterSpike.consecutiveElevatedMonths()).isZero();

        TrendResult spikeMonthItself = evaluator.evaluate(List.of(month2Elevated, false));
        assertThat(spikeMonthItself.state()).isEqualTo(State.WATCH);
        assertThat(spikeMonthItself.consecutiveElevatedMonths()).isEqualTo(1);
    }

    @Test
    void sustainedIncreaseAcrossTwoMonthsIsIncreasing() {
        // Rs20K -> Rs25K -> Rs29K: both the current and previous month are elevated vs the Rs20K
        // baseline - two months running is stronger evidence than a single spike.
        String baseline = "20000";
        boolean currentElevated = elevatedFor(baseline, "29000");
        boolean previousElevated = elevatedFor(baseline, "25000");

        TrendResult result = evaluator.evaluate(List.of(currentElevated, previousElevated));

        assertThat(result.state()).isEqualTo(State.INCREASING);
        assertThat(result.consecutiveElevatedMonths()).isEqualTo(2);
    }

    @Test
    void stableSpendingIsNeverFlaggedRegardlessOfPriorMonths() {
        TrendResult result = evaluator.evaluate(List.of(false, true, true));

        assertThat(result.state()).isEqualTo(State.STABLE);
        assertThat(result.consecutiveElevatedMonths()).isZero();
    }

    @Test
    void tinyPercentageIncreaseOnASmallAmountIsNotElevated() {
        // 100% increase but only Rs100 absolute difference - well under the Rs500 materiality floor.
        boolean elevated = elevatedFor("100", "200");

        assertThat(elevated).isFalse();
    }

    @Test
    void aSubstantialIncreaseBelowThePercentFloorIsNotElevated() {
        // Rs2000 absolute difference clears the Rs500 floor comfortably, but only a 10% increase -
        // below the 15% elevated threshold.
        boolean elevated = elevatedFor("20000", "22000");

        assertThat(elevated).isFalse();
    }

    @Test
    void aThirdConsecutiveElevatedMonthIsStillIncreasing() {
        TrendResult result = evaluator.evaluate(List.of(true, true, true));

        assertThat(result.state()).isEqualTo(State.INCREASING);
        assertThat(result.consecutiveElevatedMonths()).isEqualTo(3);
    }

    @Test
    void emptyHistoryIsTreatedAsStable() {
        TrendResult result = evaluator.evaluate(List.of());

        assertThat(result.state()).isEqualTo(State.STABLE);
        assertThat(result.consecutiveElevatedMonths()).isZero();
    }

    @Test
    void nullIncreasePercentageIsNeverElevated() {
        AnalysisResult zeroBaselineResult = analyzer.analyze(BigDecimal.ZERO, new BigDecimal("5000"));

        assertThat(evaluator.isElevated(zeroBaselineResult)).isFalse();
    }
}
