package com.prospr.app.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.repository.InsuranceRepository;

/**
 * Total active life cover, and an "Estimated Protection Gap" against
 * (annualEssentialExpenses x incomeReplacementYears) + outstandingLiabilities + futureGoals -
 * assetsAvailableForDependents. Never fabricates a target when required inputs are missing - see
 * {@link Result}. Family-floater sharing (as used for health) doesn't apply here: life policies are
 * always evaluated as directly owned by the member in v1.
 */
@Service
public class LifeCoverageService {

    private static final String LIFE = "LIFE";

    private final InsuranceRepository insuranceRepository;
    private final InsuranceActivityEvaluator activityEvaluator;

    public LifeCoverageService(InsuranceRepository insuranceRepository, InsuranceActivityEvaluator activityEvaluator) {
        this.insuranceRepository = insuranceRepository;
        this.activityEvaluator = activityEvaluator;
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
        BigDecimal liabilities = orZero(settings.getOutstandingLiabilitiesAmount());
        BigDecimal goals = orZero(settings.getFutureFinancialGoalsAmount());
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

    private boolean isLife(Insurance policy) {
        return policy.getInsuranceType() != null
                && policy.getInsuranceType().toUpperCase(Locale.ROOT).contains(LIFE);
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
