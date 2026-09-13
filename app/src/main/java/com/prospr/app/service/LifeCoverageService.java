package com.prospr.app.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.FinancialGoal;
import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.Liability;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.repository.FinancialGoalRepository;
import com.prospr.app.repository.InsuranceRepository;
import com.prospr.app.repository.LiabilityRepository;

/**
 * Total active life cover, and an "Estimated Protection Gap" against
 * (annualEssentialExpenses x incomeReplacementYears) + outstandingLiabilities + futureGoals -
 * assetsAvailableForDependents. Never fabricates a target when required inputs are missing - see
 * {@link Result}. Family-floater sharing (as used for health) doesn't apply here: life policies are
 * always evaluated as directly owned by the member in v1.
 *
 * outstandingLiabilities and futureGoals are summed live from the member's own Liability/
 * FinancialGoal records rather than read from SafetyNetSettings - once those structured entities
 * exist, a freeform lump-sum settings field feeding the same formula would risk double-counting
 * (e.g. a user who once typed "20L" into Settings, then later adds a real car loan + home loan).
 * SafetyNetSettings.outstandingLiabilitiesAmount/futureFinancialGoalsAmount are kept in the DB/API
 * for now but are no longer read here. assetsAvailableForDependents has no structured equivalent
 * requested, so it remains a manual settings field.
 */
@Service
public class LifeCoverageService {

    private static final String LIFE = "LIFE";

    private final InsuranceRepository insuranceRepository;
    private final InsuranceActivityEvaluator activityEvaluator;
    private final LiabilityRepository liabilityRepository;
    private final FinancialGoalRepository financialGoalRepository;

    public LifeCoverageService(InsuranceRepository insuranceRepository,
                                InsuranceActivityEvaluator activityEvaluator,
                                LiabilityRepository liabilityRepository,
                                FinancialGoalRepository financialGoalRepository) {
        this.insuranceRepository = insuranceRepository;
        this.activityEvaluator = activityEvaluator;
        this.liabilityRepository = liabilityRepository;
        this.financialGoalRepository = financialGoalRepository;
    }

    /**
     * @param estimatedTarget null when incomeReplacementYears or averageMonthlyEssentialExpense is
     *                        unavailable - never a fabricated number
     * @param estimatedGap    null under the same condition as estimatedTarget
     */
    public record Result(BigDecimal totalCoverage, BigDecimal estimatedTarget, BigDecimal estimatedGap,
                          String status) {
    }

    public Result compute(Member member, SafetyNetSettings settings, BigDecimal averageMonthlyEssentialExpense) {
        List<Insurance> activeLifePolicies = insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                .stream()
                .filter(this::isLife)
                .filter(activityEvaluator::isActive)
                .toList();

        BigDecimal totalCoverage = activeLifePolicies.stream()
                .map(Insurance::getSumAssured)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal incomeReplacementYears = settings.getIncomeReplacementYears();
        if (incomeReplacementYears == null || averageMonthlyEssentialExpense == null) {
            return new Result(totalCoverage, null, null, LifeCoverageStatus.REVIEW_RECOMMENDED);
        }

        BigDecimal annualEssentialExpenses = averageMonthlyEssentialExpense.multiply(BigDecimal.valueOf(12));
        BigDecimal liabilities = sumOutstandingLiabilities(member);
        BigDecimal goals = sumRemainingGoalAmounts(member);
        BigDecimal assetsAvailable = orZero(settings.getAssetsAvailableForDependents());

        BigDecimal target = annualEssentialExpenses.multiply(incomeReplacementYears)
                .add(liabilities)
                .add(goals)
                .subtract(assetsAvailable)
                .max(BigDecimal.ZERO);
        BigDecimal gap = target.subtract(totalCoverage).max(BigDecimal.ZERO);

        String status;
        if (target.compareTo(BigDecimal.ZERO) == 0) {
            status = LifeCoverageStatus.NOT_APPLICABLE;
        } else if (totalCoverage.compareTo(BigDecimal.ZERO) == 0) {
            status = LifeCoverageStatus.NO_COVER;
        } else if (gap.compareTo(BigDecimal.ZERO) > 0) {
            status = LifeCoverageStatus.GAP_DETECTED;
        } else {
            status = LifeCoverageStatus.ADEQUATE;
        }

        return new Result(totalCoverage, target, gap, status);
    }

    private BigDecimal sumOutstandingLiabilities(Member member) {
        return liabilityRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .map(Liability::getOutstandingAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Only the remaining gap toward each goal counts as a future liability against income
     *  replacement cover - not the full target amount, since progress already saved isn't "needed". */
    private BigDecimal sumRemainingGoalAmounts(Member member) {
        return financialGoalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .map(goal -> orZero(goal.getTargetAmount()).subtract(orZero(goal.getCurrentAmount())).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isLife(Insurance policy) {
        return policy.getInsuranceType() != null
                && policy.getInsuranceType().toUpperCase(Locale.ROOT).contains(LIFE);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
