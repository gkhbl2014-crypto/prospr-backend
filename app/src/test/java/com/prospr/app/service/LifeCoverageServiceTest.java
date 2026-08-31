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

import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.SafetyNetSettings;
import com.prospr.app.repository.InsuranceRepository;

@ExtendWith(MockitoExtension.class)
class LifeCoverageServiceTest {

    @Mock
    private InsuranceRepository insuranceRepository;

    private final InsuranceActivityEvaluator activityEvaluator = new InsuranceActivityEvaluator();

    private LifeCoverageService service() {
        return new LifeCoverageService(insuranceRepository, activityEvaluator);
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

    private SafetyNetSettings settings(Integer incomeReplacementYears, String liabilities, String goals, String assets) {
        return SafetyNetSettings.builder()
                .emergencyFundTargetMonths(6)
                .essentialExpenseLookbackMonths(3)
                .incomeReplacementYears(incomeReplacementYears == null ? null : BigDecimal.valueOf(incomeReplacementYears))
                .outstandingLiabilitiesAmount(liabilities == null ? null : new BigDecimal(liabilities))
                .futureFinancialGoalsAmount(goals == null ? null : new BigDecimal(goals))
                .assetsAvailableForDependents(assets == null ? null : new BigDecimal(assets))
                .build();
    }

    @Test
    void noPolicyReturnsZeroCoverage() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());

        LifeCoverageService.Result result = service().compute(member, settings(10, "0", "0", "0"),
                new BigDecimal("50000.00"));

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

        LifeCoverageService.Result result = service().compute(member, settings(10, "0", "0", "0"),
                new BigDecimal("50000.00"));

        assertThat(result.totalCoverage()).isEqualByComparingTo("7000000");
    }

    @Test
    void missingIncomeReplacementYearsReturnsReviewRecommendedWithNullTarget() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "5000000")));

        LifeCoverageService.Result result = service().compute(member, settings(null, "0", "0", "0"),
                new BigDecimal("50000.00"));

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.REVIEW_RECOMMENDED);
        assertThat(result.estimatedTarget()).isNull();
        assertThat(result.estimatedGap()).isNull();
    }

    @Test
    void missingEssentialExpenseAverageReturnsReviewRecommendedWithNullTarget() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "5000000")));

        LifeCoverageService.Result result = service().compute(member, settings(10, "0", "0", "0"), null);

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.REVIEW_RECOMMENDED);
        assertThat(result.estimatedTarget()).isNull();
    }

    @Test
    void targetCalculatedFromEssentialExpensesLiabilitiesGoalsAndAssets() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());

        // annual essential = 50000*12 = 600000; *10 years = 6,000,000; + liabilities 2,000,000
        // + goals 1,000,000 - assets 500,000 = 8,500,000
        LifeCoverageService.Result result = service().compute(member,
                settings(10, "2000000", "1000000", "500000"), new BigDecimal("50000.00"));

        assertThat(result.estimatedTarget()).isEqualByComparingTo("8500000.00");
    }

    @Test
    void positiveGapReturnsGapDetectedStatus() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "2000000")));

        LifeCoverageService.Result result = service().compute(member, settings(10, "0", "0", "0"),
                new BigDecimal("50000.00"));

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.GAP_DETECTED);
        assertThat(result.estimatedGap()).isEqualByComparingTo("4000000.00");
    }

    @Test
    void coverageAtOrAboveTargetReturnsAdequateStatus() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(lifePolicy("ACTIVE", "10000000")));

        LifeCoverageService.Result result = service().compute(member, settings(10, "0", "0", "0"),
                new BigDecimal("50000.00"));

        assertThat(result.status()).isEqualTo(LifeCoverageStatus.ADEQUATE);
        assertThat(result.estimatedGap()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
