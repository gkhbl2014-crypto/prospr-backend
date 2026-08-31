package com.prospr.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.InsuranceCoveredMember;
import com.prospr.app.entity.Member;
import com.prospr.app.repository.InsuranceCoveredMemberRepository;
import com.prospr.app.repository.InsuranceRepository;

@ExtendWith(MockitoExtension.class)
class HealthCoverageServiceTest {

    @Mock
    private InsuranceRepository insuranceRepository;
    @Mock
    private InsuranceCoveredMemberRepository coveredMemberRepository;

    private final InsuranceActivityEvaluator activityEvaluator = new InsuranceActivityEvaluator();

    private HealthCoverageService service() {
        return new HealthCoverageService(insuranceRepository, coveredMemberRepository, activityEvaluator);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).build();
    }

    private Insurance healthPolicy(String policyType, String status, BigDecimal sumInsured) {
        return Insurance.builder()
                .id(UUID.randomUUID())
                .insuranceType("HEALTH")
                .policyType(policyType)
                .policyStatus(status)
                .sumInsured(sumInsured)
                .build();
    }

    @Test
    void noPolicyReturnsNoCoverStatus() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        when(coveredMemberRepository.findByMemberId(member.getId())).thenReturn(List.of());

        HealthCoverageService.Result result = service().compute(member);

        assertThat(result.status()).isEqualTo(HealthCoverageStatus.NO_COVER);
        assertThat(result.individualCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.sharedCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.activePolicyCount()).isZero();
    }

    @Test
    void individualPolicyCountsAsIndividualCoverage() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(healthPolicy("INDIVIDUAL", "ACTIVE", new BigDecimal("500000"))));
        when(coveredMemberRepository.findByMemberId(member.getId())).thenReturn(List.of());

        HealthCoverageService.Result result = service().compute(member);

        assertThat(result.individualCoverage()).isEqualByComparingTo("500000");
        assertThat(result.sharedCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.activePolicyCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(HealthCoverageStatus.REVIEW_RECOMMENDED);
    }

    @Test
    void multipleIndividualPoliciesAreSummed() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of(
                healthPolicy("INDIVIDUAL", "ACTIVE", new BigDecimal("300000")),
                healthPolicy("INDIVIDUAL", "ACTIVE", new BigDecimal("200000"))));
        when(coveredMemberRepository.findByMemberId(member.getId())).thenReturn(List.of());

        HealthCoverageService.Result result = service().compute(member);

        assertThat(result.individualCoverage()).isEqualByComparingTo("500000");
        assertThat(result.activePolicyCount()).isEqualTo(2);
    }

    @Test
    void ownedFamilyFloaterCountsAsSharedNotIndividualCoverage() {
        Member member = member();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(healthPolicy("FAMILY_FLOATER", "ACTIVE", new BigDecimal("1000000"))));
        when(coveredMemberRepository.findByMemberId(member.getId())).thenReturn(List.of());

        HealthCoverageService.Result result = service().compute(member);

        assertThat(result.individualCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.sharedCoverage()).isEqualByComparingTo("1000000");
        assertThat(result.activePolicyCount()).isEqualTo(1);
    }

    @Test
    void memberCoveredByAnotherMembersFloaterSeesSharedCoverageOnce() {
        Member member = member();
        Insurance floater = healthPolicy("FAMILY_FLOATER", "ACTIVE", new BigDecimal("1000000"));
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of());
        when(coveredMemberRepository.findByMemberId(member.getId())).thenReturn(List.of(
                InsuranceCoveredMember.builder().insurance(floater).member(member).build()));

        HealthCoverageService.Result result = service().compute(member);

        assertThat(result.individualCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.sharedCoverage()).isEqualByComparingTo("1000000");
        assertThat(result.activePolicyCount()).isEqualTo(1);
        assertThat(result.status()).isEqualTo(HealthCoverageStatus.REVIEW_RECOMMENDED);
    }

    @Test
    void expiredPolicyIsExcludedFromCoverage() {
        Member member = member();
        Insurance expired = healthPolicy("INDIVIDUAL", "EXPIRED", new BigDecimal("500000"));
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of(expired));
        when(coveredMemberRepository.findByMemberId(member.getId())).thenReturn(List.of());

        HealthCoverageService.Result result = service().compute(member);

        assertThat(result.individualCoverage()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.status()).isEqualTo(HealthCoverageStatus.NO_COVER);
    }

    @Test
    void pastEndDateWithNoExplicitStatusIsTreatedAsInactive() {
        Member member = member();
        Insurance lapsedByDate = Insurance.builder()
                .id(UUID.randomUUID())
                .insuranceType("HEALTH")
                .policyType("INDIVIDUAL")
                .policyStatus(null)
                .policyEndDate(LocalDate.now().minusDays(1))
                .sumInsured(new BigDecimal("500000"))
                .build();
        when(insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())).thenReturn(List.of(lapsedByDate));
        when(coveredMemberRepository.findByMemberId(member.getId())).thenReturn(List.of());

        HealthCoverageService.Result result = service().compute(member);

        assertThat(result.status()).isEqualTo(HealthCoverageStatus.NO_COVER);
    }
}
