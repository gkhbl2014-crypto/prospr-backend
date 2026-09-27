package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class GoalCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 26);

    @Test
    void aGoalSavingEnoughEachMonthIsOnTrack() {
        GoalCalculator.Progress progress = GoalCalculator.evaluate(
                new BigDecimal("1000000"), new BigDecimal("250000"), new BigDecimal("30000"),
                LocalDate.of(2028, 12, 1), TODAY);

        assertThat(progress.status()).isEqualTo(GoalStatus.ON_TRACK);
        assertThat(progress.progressPercent()).isEqualByComparingTo("25");
        assertThat(progress.remainingAmount()).isEqualByComparingTo("750000");
        assertThat(progress.message()).contains("on track");
    }

    @Test
    void aGoalSavingLessThanRequiredNeedsAttention() {
        // The spec's own worked example: 2.5L saved of 10L, ~27.8K/month required, 25K/month actual.
        GoalCalculator.Progress progress = GoalCalculator.evaluate(
                new BigDecimal("1000000"), new BigDecimal("250000"), new BigDecimal("25000"),
                LocalDate.of(2028, 12, 1), TODAY);

        assertThat(progress.status()).isEqualTo(GoalStatus.NEEDS_ATTENTION);
        assertThat(progress.requiredMonthlyContribution()).isEqualByComparingTo("26786");
        assertThat(progress.message()).contains("26,786").contains("25,000").contains("250,000");
    }

    @Test
    void aGoalWhoseCurrentAmountMeetsOrExceedsTheTargetIsCompleted() {
        GoalCalculator.Progress progress = GoalCalculator.evaluate(
                new BigDecimal("500000"), new BigDecimal("500000"), new BigDecimal("0"),
                LocalDate.of(2027, 1, 1), TODAY);

        assertThat(progress.status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(progress.remainingAmount()).isEqualByComparingTo("0");
        assertThat(progress.progressPercent()).isEqualByComparingTo("100");
        assertThat(progress.message()).contains("congratulations");
    }

    @Test
    void aGoalPastItsTargetDateWithoutBeingCompletedNeedsAttention() {
        GoalCalculator.Progress progress = GoalCalculator.evaluate(
                new BigDecimal("1000000"), new BigDecimal("250000"), new BigDecimal("25000"),
                LocalDate.of(2026, 1, 1), TODAY);

        assertThat(progress.status()).isEqualTo(GoalStatus.NEEDS_ATTENTION);
        assertThat(progress.monthsRemaining()).isZero();
        assertThat(progress.message()).contains("target date has passed");
    }

    @Test
    void progressPercentIsNeverReportedAboveOneHundredEvenWhenOversaved() {
        GoalCalculator.Progress progress = GoalCalculator.evaluate(
                new BigDecimal("500000"), new BigDecimal("600000"), new BigDecimal("0"),
                LocalDate.of(2027, 1, 1), TODAY);

        assertThat(progress.progressPercent()).isEqualByComparingTo("100");
        assertThat(progress.status()).isEqualTo(GoalStatus.COMPLETED);
    }
}
