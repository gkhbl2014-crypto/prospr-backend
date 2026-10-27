package com.prospr.app.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.prospr.app.dto.request.WhatIfRequest;
import com.prospr.app.dto.response.SafetyNetResponse;
import com.prospr.app.dto.response.WhatIfResponse;
import com.prospr.app.entity.Goal;
import com.prospr.app.entity.Income;
import com.prospr.app.entity.Liability;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MemberMonthlySnapshot;
import com.prospr.app.exception.WhatIfValidationException;
import com.prospr.app.repository.GoalRepository;
import com.prospr.app.repository.IncomeRepository;
import com.prospr.app.repository.LiabilityRepository;
import com.prospr.app.repository.MemberMonthlySnapshotRepository;

/**
 * Answers "what happens to my finances if I take this loan / spend this / add this cost?" using only
 * data already in Prospr. Gathers the member's baseline here, then hands the maths and the judgement
 * to {@link WhatIfCalculator}. Read-only: nothing is written and nothing leaves the application.
 */
@Service
public class WhatIfService {

    private static final int BASELINE_MONTHS = 3;
    private static final String MONTHLY = "MONTHLY";

    private final MemberMonthlySnapshotRepository snapshotRepository;
    private final LiabilityRepository liabilityRepository;
    private final GoalRepository goalRepository;
    private final IncomeRepository incomeRepository;
    private final SafetyNetService safetyNetService;
    private final CurrentMonthResolver currentMonthResolver;

    public WhatIfService(MemberMonthlySnapshotRepository snapshotRepository,
                         LiabilityRepository liabilityRepository,
                         GoalRepository goalRepository,
                         IncomeRepository incomeRepository,
                         SafetyNetService safetyNetService,
                         CurrentMonthResolver currentMonthResolver) {
        this.snapshotRepository = snapshotRepository;
        this.liabilityRepository = liabilityRepository;
        this.goalRepository = goalRepository;
        this.incomeRepository = incomeRepository;
        this.safetyNetService = safetyNetService;
        this.currentMonthResolver = currentMonthResolver;
    }

    /** What the monthly figures were built from, so the response can say how much to trust them.
     *  {@code otherCreditsCounted}: non-salary money received (e.g. UPI) is part of {@code income}.
     *  {@code cashIncome}: the member-stated monthly cash that was added on top. */
    private record Spending(BigDecimal income, BigDecimal savings, int months, boolean incomeFromManualEntry,
                            boolean partialMonth, boolean otherCreditsCounted, BigDecimal cashIncome) {
    }

    public WhatIfResponse evaluate(Member member, WhatIfRequest request) {
        if (WhatIfCalculator.LOAN.equals(request.getType()) && request.getTenureMonths() == null) {
            throw new WhatIfValidationException("A loan needs a repayment period (number of months).");
        }

        Spending spending = loadSpending(member, orZero(request.getExtraMonthlyIncome()));
        SafetyNetResponse.EmergencyFund fund = safetyNetFund(member);
        BigDecimal emergencyFund = fund == null ? BigDecimal.ZERO : orZero(fund.getCurrentAmount());
        BigDecimal essential = fund == null ? BigDecimal.ZERO : orZero(fund.getAverageMonthlyEssentialExpense());

        WhatIfCalculator.Baseline baseline = new WhatIfCalculator.Baseline(
                spending.income(), spending.savings(), existingEmi(member), emergencyFund, essential,
                goalsMonthlyNeed(member));
        WhatIfCalculator.Scenario scenario = new WhatIfCalculator.Scenario(
                request.getType(), request.getAmount(), request.getAnnualInterestRate(), request.getTenureMonths());

        return WhatIfCalculator.evaluate(baseline, scenario).toBuilder()
                .label(request.getLabel() == null || request.getLabel().isBlank() ? null : request.getLabel().trim())
                .basedOnMonths(spending.months())
                .notes(notes(request, spending, emergencyFund))
                .build();
    }

    /**
     * Averages the last few finished months of imported transactions. A month still in progress is
     * left out (it would understate spending) unless it is all there is.
     *
     * <p>Income here is money received that is actually earnings: salary the classifier recognised
     * plus other incoming credits (typically UPI payments from people). Refunds and transfers between
     * the member's own accounts are left out - they are not income and would flatter the answer.
     * Cash the member says they earn is then added on top.
     */
    private Spending loadSpending(Member member, BigDecimal cashIncome) {
        List<MemberMonthlySnapshot> withData = snapshotRepository
                .findByMemberIdOrderByYearDescMonthDesc(member.getId()).stream()
                .filter(s -> Boolean.TRUE.equals(s.getHasData()))
                .toList();

        CurrentMonthResolver.EffectivePeriod period = currentMonthResolver.resolve(member.getId());
        boolean currentMonthInProgress = !period.asOfDate().equals(period.month().atEndOfMonth());
        List<MemberMonthlySnapshot> finished = withData.stream()
                .filter(s -> !(currentMonthInProgress
                        && YearMonth.of(s.getYear(), s.getMonth()).equals(period.month())))
                .toList();
        boolean partialMonth = finished.isEmpty() && !withData.isEmpty();
        List<MemberMonthlySnapshot> window = (partialMonth ? withData : finished).stream()
                .limit(BASELINE_MONTHS)
                .toList();

        if (window.isEmpty()) {
            return new Spending(BigDecimal.ZERO, BigDecimal.ZERO, 0, false, partialMonth, false, BigDecimal.ZERO);
        }

        List<MemberMonthlySnapshot> withCredits = window.stream()
                .filter(s -> receivedAsIncome(s).signum() > 0)
                .toList();
        BigDecimal income;
        BigDecimal outflow;
        int months;
        boolean fromManualEntry = false;
        boolean otherCreditsCounted = false;
        if (!withCredits.isEmpty()) {
            income = average(withCredits, WhatIfService::receivedAsIncome);
            outflow = average(withCredits, MemberMonthlySnapshot::getTotalCashOutflow);
            months = withCredits.size();
            otherCreditsCounted = withCredits.stream().anyMatch(s -> orZero(s.getTotalOtherCredit()).signum() > 0);
        } else {
            // Nothing came in through the statements: fall back to the income the member typed in.
            BigDecimal manualIncome = incomeRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                    .filter(i -> MONTHLY.equals(i.getFrequency()))
                    .map(Income::getMonthlyAmount)
                    .map(WhatIfService::orZero)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            income = manualIncome;
            outflow = average(window, MemberMonthlySnapshot::getTotalCashOutflow);
            months = window.size();
            fromManualEntry = manualIncome.signum() > 0;
        }

        income = income.add(cashIncome);
        return new Spending(income, income.subtract(outflow), months, fromManualEntry, partialMonth,
                otherCreditsCounted, cashIncome);
    }

    /** Salary plus other incoming credits; refunds and own-account transfers are separate columns and excluded. */
    private static BigDecimal receivedAsIncome(MemberMonthlySnapshot snapshot) {
        return orZero(snapshot.getTotalIncome()).add(orZero(snapshot.getTotalOtherCredit()));
    }

    private SafetyNetResponse.EmergencyFund safetyNetFund(Member member) {
        SafetyNetResponse response = safetyNetService.getForMember(member);
        return response == null ? null : response.getEmergencyFund();
    }

    /** Monthly EMIs on loans that are still running. */
    private BigDecimal existingEmi(Member member) {
        LocalDate today = LocalDate.now();
        return liabilityRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .filter(l -> l.getEndDate() == null || !l.getEndDate().isBefore(today))
                .map(Liability::getEmiAmount)
                .map(WhatIfService::orZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What the member's own goals ask them to put aside each month. */
    private BigDecimal goalsMonthlyNeed(Member member) {
        LocalDate today = LocalDate.now();
        BigDecimal total = BigDecimal.ZERO;
        for (Goal goal : goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())) {
            BigDecimal target = orZero(goal.getTargetAmount());
            BigDecimal remaining = target.subtract(orZero(goal.getCurrentAmount()));
            if (remaining.signum() <= 0) {
                continue;
            }
            if (orZero(goal.getMonthlyContribution()).signum() > 0) {
                total = total.add(goal.getMonthlyContribution());
            } else if (goal.getTargetDate() != null && goal.getTargetDate().isAfter(today)) {
                long monthsLeft = Math.max(1, ChronoUnit.MONTHS.between(today, goal.getTargetDate()));
                total = total.add(remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.HALF_UP));
            }
        }
        return total;
    }

    private List<String> notes(WhatIfRequest request, Spending spending, BigDecimal emergencyFund) {
        List<String> notes = new ArrayList<>();
        if (spending.months() > 0) {
            notes.add("Based on your average over the last " + spending.months()
                    + (spending.months() == 1 ? " month" : " months") + " of imported transactions.");
        }
        if (spending.months() > 0 && spending.months() < BASELINE_MONTHS) {
            notes.add("There is little history to go on, so treat this as a rough estimate.");
        }
        if (spending.partialMonth()) {
            notes.add("The only month available isn't finished yet, so spending may be understated.");
        }
        if (spending.otherCreditsCounted()) {
            notes.add("Money you received that isn't a salary (for example UPI payments) was counted as income. "
                    + "Refunds and transfers between your own accounts were left out. If some of it was a loan "
                    + "or a repayment from a friend, your real income is lower.");
        }
        if (spending.incomeFromManualEntry()) {
            notes.add("No money coming in was found in the statements, so the income you entered was used.");
        }
        if (spending.cashIncome().signum() > 0) {
            notes.add("You said you earn about " + spending.cashIncome().setScale(0, RoundingMode.HALF_UP)
                    .toPlainString() + " a month in cash. It was added to your income and counted as money "
                    + "available to save, which assumes you don't also spend that cash on things Prospr can't see.");
        }
        switch (request.getType()) {
            case WhatIfCalculator.LOAN -> notes.add("Assumes the borrowed money is spent on the purchase "
                    + "(none of it adds to your savings) and the EMI stays fixed for the whole period.");
            case WhatIfCalculator.ONE_TIME_SPEND -> {
                notes.add("Assumes the amount is paid from your savings.");
                if (emergencyFund.signum() <= 0) {
                    notes.add("No emergency fund is set up yet, so the answer rests on how long your savings "
                            + "would take to recover. Set one up under Safety Net for a sharper answer.");
                }
            }
            default -> notes.add("Assumes the cost continues every month.");
        }
        notes.add("Thresholds such as EMIs under 30-40% of income, saving at least 10% and keeping 3 months "
                + "of essentials are common rules of thumb. This is a planning aid, not financial advice.");
        return notes;
    }

    private static BigDecimal average(List<MemberMonthlySnapshot> snapshots,
                                      Function<MemberMonthlySnapshot, BigDecimal> field) {
        BigDecimal sum = snapshots.stream().map(field).map(WhatIfService::orZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(snapshots.size()), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
