package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.FinancialGoal;
import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.Liability;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.repository.FinancialGoalRepository;
import com.prospr.app.repository.InsuranceRepository;
import com.prospr.app.repository.LiabilityRepository;

@ExtendWith(MockitoExtension.class)
class LifeCoverageServiceTest {

    @Mock
    private InsuranceRepository insuranceRepository;
    @Mock
    private LiabilityRepository liabilityRepository;
    @Mock
    private FinancialGoalRepository financialGoalRepository;

    private final InsuranceActivityEvaluator activityEvaluator = new InsuranceActivityEvaluator();

    private LifeCoverageService service() {
        return new LifeCoverageService(insuranceRepository, activityEvaluator, liabilityRepository, financialGoalRepository);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private Insurance lifePolicy(String status, String sumAssured) {
        return Insurance.builder()
                .id(UUID.randomUUID())
                .insuranceType("LIFE")
                .policyStatus(status)
                .sumAssured(new BigDecimal(sumAssured))
                .build();
    }

    private SafetyNetSettings settings(Integer incomeReplacementYears, String assets) {
        return SafetyNetSettings.builder()
                .emergencyFundTargetMonths(6)
                .essentialExpenseLookbackMonths(3)
                .incomeReplacementYears(incomeReplacementYears == null ? null : BigDecimal.valueOf(incomeReplacementYears))
                .assetsAvailableForDependents(assets == null ? null : new BigDecimal(assets))
                .build();
    }

    private Liability liability(String outstandingAmount) {
        return Liability.builder().outstandingAmount(new BigDecimal(outstandingAmount)).build();
    }

    private FinancialGoal goal(String targetAmount, String currentAmount) {
        return FinancialGoal.builder()
                .targetAmount(new BigDecimal(targetAmount))
                .currentAmount(new BigDecimal(currentAmount))
                .build();
    }

    /** Every test that reaches the full target calculation (i.e. incomeReplacementYears and
     *  averageMonthlyEssentialExpense are both non-null) must stub these two, since compute() sums
     *  live Liability/FinancialGoal records rather than reading a settings lump sum. */
    private void noLiabilitiesOrGoals(Member member) {
        when(liabilityRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        when(financialGoalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
    }

    @Test
    void noPolicyReturnsZeroCoverage() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        noLiabilitiesOrGoals(member);

        LifeCoverageService.Result result = service().compute(member, settings(10, "0"), new BigDecimal("50000.00"));

        assertThat(result.totalCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.status()).isEqualTo(LifeCoverageStatus.NO_COVER);
    }

    @Test
    void activePoliciesAreSummedAndExpiredOnesExcluded() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of(
                lifePolicy("ACTIVE", "5000000"),
                lifePolicy("ACTIVE", "2000000"),
                lifePolicy("EXPIRED", "1000000")));
        noLiabilitiesOrGoals(member);

        LifeCoverageService.Result result = service().compute(member, settings(10, "0"), new BigDecimal("50000.00"));

        assertThat(result.totalCoverage()).isEqualByComparingTo("7000000");
    }

    @Test
    void missingIncomeReplacementYearsReturnsReviewRecommendedWithNullTarget() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "5000000")));

        LifeCoverageService.Result result = service().compute(member, settings(null, "0"), new BigDecimal("50000.00"));

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.REVIEW_RECOMMENDED);
        assertThat(result.estimatedTarget()).isNull();
        assertThat(result.estimatedGap()).isNull();
    }

    @Test
    void missingEssentialExpenseAverageReturnsReviewRecommendedWithNullTarget() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "5000000")));

        LifeCoverageService.Result result = service().compute(member, settings(10, "0"), null);

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.REVIEW_RECOMMENDED);
        assertThat(result.estimatedTarget()).isNull();
    }

    @Test
    void targetCalculatedFromEssentialExpensesLiabilitiesGoalsAndAssets() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        when(liabilityRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(liability("2000000")));
        when(financialGoalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(goal("1000000", "0")));

        // annual essential = 50000*12 = 600000; *10 years = 6,000,000; + liabilities 2,000,000
        // + remaining goal 1,000,000 - assets 500,000 = 8,500,000
        LifeCoverageService.Result result = service().compute(member, settings(10, "500000"), new BigDecimal("50000.00"));

        assertThat(result.estimatedTarget()).isEqualByComparingTo("8500000.00");
    }

    @Test
    void targetSumsOutstandingAmountAcrossMultipleLiabilities() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        when(liabilityRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(liability("1500000"), liability("500000")));
        when(financialGoalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());

        // annual essential = 0 (avg expense 0) -> base 0; + liabilities (1.5M + 0.5M) = 2,000,000
        LifeCoverageService.Result result = service().compute(member, settings(10, "0"), BigDecimal.ZERO);

        assertThat(result.estimatedTarget()).isEqualByComparingTo("2000000");
    }

    @Test
    void targetSumsOnlyRemainingGapPerGoalNotFullTargetAmount() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        when(liabilityRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        // Goal 1: target 1,000,000, already saved 400,000 -> remaining 600,000.
        // Goal 2: target 200,000, already saved 250,000 -> remaining gap floored at 0, not negative.
        when(financialGoalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(goal("1000000", "400000"), goal("200000", "250000")));

        LifeCoverageService.Result result = service().compute(member, settings(10, "0"), BigDecimal.ZERO);

        assertThat(result.estimatedTarget()).isEqualByComparingTo("600000");
    }

    @Test
    void positiveGapReturnsGapDetectedStatus() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "2000000")));
        noLiabilitiesOrGoals(member);

        LifeCoverageService.Result result = service().compute(member, settings(10, "0"), new BigDecimal("50000.00"));

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.GAP_DETECTED);
        assertThat(result.estimatedGap()).isEqualByComparingTo("4000000.00");
    }

    @Test
    void coverageAtOrAboveTargetReturnsAdequateStatus() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "10000000")));
        noLiabilitiesOrGoals(member);

        LifeCoverageService.Result result = service().compute(member, settings(10, "0"), new BigDecimal("50000.00"));

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.ADEQUATE);
        assertThat(result.estimatedGap()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
