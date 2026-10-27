package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.prospr.app.dto.response.WhatIfResponse;
import com.prospr.app.dto.response.WhatIfResponse.Position;
import com.prospr.app.dto.response.WhatIfResponse.Reason;

/**
 * The arithmetic and the rules behind "what if I...?". No database, no clock, no randomness - the
 * same baseline and scenario always give the same answer, and every figure can be traced to a line
 * here. The thresholds are common personal-finance rules of thumb, not advice; the response says so.
 */
public final class WhatIfCalculator {

    public static final String LOAN = "LOAN";
    public static final String ONE_TIME_SPEND = "ONE_TIME_SPEND";
    public static final String MONTHLY_COST = "MONTHLY_COST";

    public static final String COMFORTABLE = "COMFORTABLE";
    public static final String TIGHT = "TIGHT";
    public static final String RISKY = "RISKY";
    public static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    static final String SEV_OK = "OK";
    static final String SEV_TIGHT = "TIGHT";
    static final String SEV_RISKY = "RISKY";
    static final String SEV_INFO = "INFO";

    /** EMIs above this share of income are widely treated as unsafe. */
    static final BigDecimal UNSAFE_EMI_SHARE = new BigDecimal("0.40");
    /** Above this share the budget gets stretched. */
    static final BigDecimal STRETCHED_EMI_SHARE = new BigDecimal("0.30");
    /** Saving less than this fraction of income leaves little room for surprises. */
    static final BigDecimal HEALTHY_SAVINGS_RATE = new BigDecimal("0.10");
    static final BigDecimal UNSAFE_FUND_MONTHS = BigDecimal.ONE;
    static final BigDecimal COMFORTABLE_FUND_MONTHS = new BigDecimal("3");
    /** A one-time spend that takes longer than this to save back is flagged. */
    static final BigDecimal LONG_REBUILD_MONTHS = new BigDecimal("6");

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final Locale INDIA = Locale.of("en", "IN");

    /** Where the member stands today. All amounts are per month unless noted. */
    public record Baseline(BigDecimal monthlyIncome, BigDecimal monthlySavings, BigDecimal existingEmi,
                           BigDecimal emergencyFund, BigDecimal monthlyEssentialExpense,
                           BigDecimal goalsMonthlyNeed) {
    }

    public record Scenario(String type, BigDecimal amount, BigDecimal annualInterestRate, Integer tenureMonths) {
    }

    private WhatIfCalculator() {
    }

    /** Standard reducing-balance EMI. A zero or missing rate splits the amount evenly. */
    public static BigDecimal emi(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (annualRatePercent == null || annualRatePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(months, MC);
        return principal.multiply(monthlyRate, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    public static WhatIfResponse evaluate(Baseline base, Scenario scenario) {
        boolean loan = LOAN.equals(scenario.type());
        boolean oneTime = ONE_TIME_SPEND.equals(scenario.type());

        BigDecimal commitment = BigDecimal.ZERO;
        BigDecimal upfront = BigDecimal.ZERO;
        BigDecimal totalCost;
        BigDecimal interest = BigDecimal.ZERO;
        if (loan) {
            int months = scenario.tenureMonths();
            commitment = emi(scenario.amount(), scenario.annualInterestRate(), months);
            totalCost = commitment.multiply(BigDecimal.valueOf(months));
            interest = totalCost.subtract(scenario.amount()).max(BigDecimal.ZERO);
        } else if (oneTime) {
            upfront = scenario.amount();
            totalCost = scenario.amount();
        } else {
            commitment = scenario.amount();
            totalCost = scenario.amount().multiply(BigDecimal.valueOf(12));
        }

        BigDecimal emiAfter = base.existingEmi().add(loan ? commitment : BigDecimal.ZERO);
        BigDecimal savingsAfter = base.monthlySavings().subtract(commitment);
        BigDecimal fundAfter = base.emergencyFund().subtract(upfront).max(BigDecimal.ZERO);
        // Only meaningful when a fund is recorded; with none, "shortfall" would just repeat the whole amount.
        BigDecimal fundShortfall = base.emergencyFund().signum() > 0
                ? upfront.subtract(base.emergencyFund()).max(BigDecimal.ZERO) : BigDecimal.ZERO;

        Position before = position(base, base.monthlySavings(), base.existingEmi(), base.emergencyFund());
        Position after = position(base, savingsAfter, emiAfter, fundAfter);

        BigDecimal monthsToRebuild = oneTime && base.monthlySavings().signum() > 0
                ? upfront.divide(base.monthlySavings(), 1, RoundingMode.HALF_UP) : null;

        List<Reason> reasons = new ArrayList<>();
        String verdict;
        if (base.monthlyIncome().signum() <= 0) {
            verdict = INSUFFICIENT_DATA;
            reasons.add(reason(SEV_INFO, "I couldn't find any money coming in from your imported statements yet, "
                    + "so I can't judge how this fits. Import your bank statements or add your income, then try again."));
        } else {
            judge(base, scenario, commitment, upfront, before, after, fundShortfall, monthsToRebuild, reasons);
            verdict = verdictOf(reasons);
            if (reasons.isEmpty()) {
                reasons.add(reason(SEV_OK, comfortableMessage(after, oneTime, monthsToRebuild)));
                verdict = COMFORTABLE;
            }
        }

        return WhatIfResponse.builder()
                .verdict(verdict)
                .type(scenario.type())
                .monthlyCommitment(commitment)
                .upfrontAmount(upfront)
                .totalCost(totalCost)
                .totalInterest(interest)
                .before(before)
                .after(after)
                .monthsToRebuild(monthsToRebuild)
                .fundShortfall(fundShortfall)
                .reasons(reasons)
                .build();
    }

    private static void judge(Baseline base, Scenario scenario, BigDecimal commitment, BigDecimal upfront,
                               Position before, Position after, BigDecimal fundShortfall,
                               BigDecimal monthsToRebuild, List<Reason> reasons) {
        boolean loan = LOAN.equals(scenario.type());
        boolean oneTime = ONE_TIME_SPEND.equals(scenario.type());

        // 1. Does the monthly budget still balance?
        if (after.getMonthlySavings().signum() < 0) {
            reasons.add(reason(SEV_RISKY, "Your monthly spending would be " + inr(after.getMonthlySavings().negate())
                    + " more than your income, so you'd be dipping into savings or borrowing more every month."));
        } else if (commitment.signum() > 0 && after.getSavingsRate().compareTo(HEALTHY_SAVINGS_RATE) < 0) {
            reasons.add(reason(SEV_TIGHT, "Your savings rate would fall from " + pct(before.getSavingsRate())
                    + " to " + pct(after.getSavingsRate()) + ", below the 10% many people try to protect."));
        }

        // 2. Loans: how much of income goes to EMIs.
        if (loan) {
            BigDecimal share = after.getEmiToIncome();
            if (share.compareTo(UNSAFE_EMI_SHARE) > 0) {
                reasons.add(reason(SEV_RISKY, "EMIs would take " + pct(share) + " of your income. "
                        + "Above 40% is generally considered unsafe."));
            } else if (share.compareTo(STRETCHED_EMI_SHARE) > 0) {
                reasons.add(reason(SEV_TIGHT, "EMIs would take " + pct(share) + " of your income, up from "
                        + pct(before.getEmiToIncome()) + ". Above 30% starts to stretch a budget."));
            }
        }

        // 3. One-time spends: what's left of the cushion.
        if (oneTime) {
            judgeOneTimeSpend(base, upfront, after, fundShortfall, monthsToRebuild, reasons);
        }

        // 4. Goals: does the plan for them still hold?
        if (after.getGoalsShortfall().subtract(before.getGoalsShortfall()).compareTo(BigDecimal.ONE) > 0) {
            reasons.add(reason(SEV_TIGHT, "Your goals need " + inr(base.goalsMonthlyNeed()) + " a month. "
                    + "You'd be " + inr(after.getGoalsShortfall()) + " short each month"
                    + (before.getGoalsShortfall().signum() > 0
                            ? " (already " + inr(before.getGoalsShortfall()) + " short)" : "") + "."));
        }
    }

    private static void judgeOneTimeSpend(Baseline base, BigDecimal upfront, Position after,
                                           BigDecimal fundShortfall, BigDecimal monthsToRebuild,
                                           List<Reason> reasons) {
        boolean fundKnown = base.emergencyFund().signum() > 0;
        if (fundKnown) {
            if (fundShortfall.signum() > 0) {
                reasons.add(reason(SEV_RISKY, "This costs " + inr(fundShortfall) + " more than your whole emergency fund, "
                        + "so the rest would have to be borrowed."));
                return;
            }
            BigDecimal monthsLeft = after.getEmergencyFundMonths();
            if (monthsLeft != null && monthsLeft.compareTo(UNSAFE_FUND_MONTHS) < 0) {
                reasons.add(reason(SEV_RISKY, "It would leave your emergency fund covering only "
                        + months(monthsLeft) + " of essential spending."));
                return;
            }
            if (monthsLeft != null && monthsLeft.compareTo(COMFORTABLE_FUND_MONTHS) < 0) {
                reasons.add(reason(SEV_TIGHT, "It would leave your emergency fund covering " + months(monthsLeft)
                        + " of essential spending. Most people aim for at least 3."));
                return;
            }
        }
        // No fund recorded, or the fund holds up: judge by how long the money takes to come back.
        if (base.monthlySavings().signum() <= 0) {
            reasons.add(reason(SEV_RISKY, "You aren't saving anything right now, so " + inr(upfront)
                    + " would come out of money you may need, or be borrowed."));
        } else if (monthsToRebuild != null && monthsToRebuild.compareTo(LONG_REBUILD_MONTHS) > 0) {
            reasons.add(reason(SEV_TIGHT, "At your usual savings it would take about " + months(monthsToRebuild)
                    + " to put this money back."));
        }
    }

    private static Position position(Baseline base, BigDecimal savings, BigDecimal emiTotal, BigDecimal fund) {
        BigDecimal income = base.monthlyIncome();
        boolean hasIncome = income.signum() > 0;
        BigDecimal essential = base.monthlyEssentialExpense();
        BigDecimal covered = savings.max(BigDecimal.ZERO);
        return Position.builder()
                .monthlyIncome(income)
                .monthlySavings(savings)
                .savingsRate(hasIncome ? savings.divide(income, 4, RoundingMode.HALF_UP) : null)
                .totalEmi(emiTotal)
                .emiToIncome(hasIncome ? emiTotal.divide(income, 4, RoundingMode.HALF_UP) : null)
                .emergencyFundAmount(fund)
                .emergencyFundMonths(essential.signum() > 0 ? fund.divide(essential, 1, RoundingMode.HALF_UP) : null)
                .goalsShortfall(base.goalsMonthlyNeed().subtract(covered).max(BigDecimal.ZERO))
                .build();
    }

    private static String verdictOf(List<Reason> reasons) {
        if (reasons.stream().anyMatch(r -> SEV_RISKY.equals(r.getSeverity()))) {
            return RISKY;
        }
        return reasons.isEmpty() ? COMFORTABLE : TIGHT;
    }

    private static String comfortableMessage(Position after, boolean oneTime, BigDecimal monthsToRebuild) {
        String base = "You'd still save " + inr(after.getMonthlySavings()) + " a month (" + pct(after.getSavingsRate())
                + " of income).";
        return oneTime && monthsToRebuild != null
                ? base + " Your usual saving would put this money back in about " + months(monthsToRebuild) + "."
                : base;
    }

    private static Reason reason(String severity, String message) {
        return Reason.builder().severity(severity).message(message).build();
    }

    private static String pct(BigDecimal fraction) {
        return fraction == null ? "n/a"
                : fraction.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String months(BigDecimal value) {
        BigDecimal rounded = value.setScale(1, RoundingMode.HALF_UP);
        return rounded.stripTrailingZeros().toPlainString() + (rounded.compareTo(BigDecimal.ONE) == 0 ? " month" : " months");
    }

    private static String inr(BigDecimal amount) {
        return "₹" + NumberFormat.getIntegerInstance(INDIA).format(amount.setScale(0, RoundingMode.HALF_UP));
    }
}
