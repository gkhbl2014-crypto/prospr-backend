package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;

/**
 * Pure, deterministic goal-progress math - no database access, no AI/LLM, just arithmetic on the values
 * a member entered. Mirrors {@link LifestyleTrendEvaluator}'s shape: a small stateless calculator kept
 * separate from persistence so the numbers/status/message can be unit-tested in isolation.
 */
public final class GoalCalculator {

    private static final Locale INDIA = Locale.of("en", "IN");

    private GoalCalculator() {
    }

    public record Progress(
            BigDecimal progressPercent,
            BigDecimal remainingAmount,
            long monthsRemaining,
            BigDecimal requiredMonthlyContribution,
            String status,
            String message) {
    }

    public static Progress evaluate(BigDecimal targetAmount, BigDecimal currentAmount,
                                     BigDecimal monthlyContribution, LocalDate targetDate, LocalDate today) {
        BigDecimal remainingAmount = targetAmount.subtract(currentAmount).max(BigDecimal.ZERO);
        boolean completed = remainingAmount.signum() <= 0;
        boolean overdue = !completed && targetDate.isBefore(today);

        BigDecimal progressPercent = targetAmount.signum() > 0
                ? currentAmount.multiply(BigDecimal.valueOf(100)).divide(targetAmount, 0, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        long monthsRemaining = Math.max(0,
                Period.between(today.withDayOfMonth(1), targetDate.withDayOfMonth(1)).toTotalMonths());
        long effectiveMonths = Math.max(monthsRemaining, 1);
        BigDecimal requiredMonthlyContribution = remainingAmount
                .divide(BigDecimal.valueOf(effectiveMonths), 0, RoundingMode.HALF_UP);

        String status;
        if (completed) {
            status = GoalStatus.COMPLETED;
        } else if (overdue || monthlyContribution.compareTo(requiredMonthlyContribution) < 0) {
            status = GoalStatus.NEEDS_ATTENTION;
        } else {
            status = GoalStatus.ON_TRACK;
        }

        String message = buildMessage(targetAmount, currentAmount, monthlyContribution, remainingAmount,
                requiredMonthlyContribution, overdue, status);

        return new Progress(progressPercent, remainingAmount, monthsRemaining, requiredMonthlyContribution, status, message);
    }

    private static String buildMessage(BigDecimal targetAmount, BigDecimal currentAmount, BigDecimal monthlyContribution,
                                        BigDecimal remainingAmount, BigDecimal requiredMonthlyContribution,
                                        boolean overdue, String status) {
        if (GoalStatus.COMPLETED.equals(status)) {
            return "You've reached your " + formatInr(targetAmount) + " goal - congratulations! You have "
                    + formatInr(currentAmount) + " saved.";
        }
        if (overdue) {
            return "Your target date has passed and you still need " + formatInr(remainingAmount)
                    + " to reach your " + formatInr(targetAmount)
                    + " goal. Consider increasing your monthly contribution or setting a new target date.";
        }
        String base = "You have " + formatInr(currentAmount) + " saved toward your " + formatInr(targetAmount)
                + " goal. You need approximately " + formatInr(requiredMonthlyContribution)
                + "/month to reach your target, while you're currently saving " + formatInr(monthlyContribution) + "/month.";
        return base + (GoalStatus.ON_TRACK.equals(status)
                ? " You're on track to reach this goal."
                : " Consider increasing your monthly contribution to stay on track.");
    }

    private static String formatInr(BigDecimal amount) {
        return "₹" + NumberFormat.getIntegerInstance(INDIA).format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
