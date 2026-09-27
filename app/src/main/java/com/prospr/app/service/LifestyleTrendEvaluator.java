package com.prospr.app.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.prospr.app.config.LifestyleProperties;

/**
 * Turns a sequence of month-by-month {@link LifestyleCreepAnalyzer.AnalysisResult}s - all computed
 * against the SAME baseline, newest month first - into a consecutive-elevated-month streak and one
 * of three simple states. Pure and DB-free, like {@link LifestyleCreepAnalyzer}: no repository/entity
 * dependency, so the spike-vs-sustained decision is directly unit-testable.
 *
 * <p>Distinguishes a one-time spike from a sustained increase using nothing more than a streak count:
 * ₹20K→₹35K→₹21K only has the middle month elevated (the streak resets once spending drops back), so
 * it never reaches INCREASING; ₹20K→₹25K→₹29K stays elevated two months running, which does.
 */
@Component
public class LifestyleTrendEvaluator {

    public enum State {
        STABLE, WATCH, INCREASING
    }

    public record TrendResult(State state, int consecutiveElevatedMonths) {
    }

    private final LifestyleProperties properties;

    public LifestyleTrendEvaluator(LifestyleProperties properties) {
        this.properties = properties;
    }

    /** "Elevated" requires BOTH a percentage floor ({@code lifestyle.low-threshold-percent}) AND an
     *  absolute-rupee floor ({@code lifestyle.min-absolute-difference-inr}), so a tiny amount doubling
     *  on a near-zero baseline is never flagged - the same materiality guard the old insightWorthy
     *  gate used, just no longer also requiring a much stricter 30%/₹500 double-gate to show anything
     *  at all. */
    public boolean isElevated(LifestyleCreepAnalyzer.AnalysisResult result) {
        if (result.increasePercentage() == null) {
            return false;
        }
        return result.increasePercentage() >= properties.getLowThresholdPercent()
                && result.differenceAmount().abs()
                        .compareTo(BigDecimal.valueOf(properties.getMinAbsoluteDifferenceInr())) >= 0;
    }

    /**
     * @param elevatedNewestFirst whether each month was elevated, current month first, then the
     *                            previous month, then the one before that, etc. - all judged against
     *                            the same baseline. Counting stops at the first non-elevated month
     *                            (or the end of the list), so a spike-then-normal pattern never
     *                            accumulates a streak from unrelated earlier months.
     */
    public TrendResult evaluate(List<Boolean> elevatedNewestFirst) {
        if (elevatedNewestFirst.isEmpty() || !elevatedNewestFirst.get(0)) {
            return new TrendResult(State.STABLE, 0);
        }
        int streak = 0;
        for (boolean elevated : elevatedNewestFirst) {
            if (!elevated) {
                break;
            }
            streak++;
        }
        State state = streak >= 2 ? State.INCREASING : State.WATCH;
        return new TrendResult(state, streak);
    }
}
