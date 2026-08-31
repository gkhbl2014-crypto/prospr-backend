package com.prospr.app.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.InsuranceCoveredMember;
import com.prospr.app.entity.Member;
import com.prospr.app.repository.InsuranceCoveredMemberRepository;
import com.prospr.app.repository.InsuranceRepository;

/**
 * Individual vs. shared (family-floater) health coverage for one member. A family floater's full
 * sum insured is counted once per policy, never once per covered member, and never folded into
 * {@code individualCoverage} - see {@link Result}.
 */
@Service
public class HealthCoverageService {

    private static final String HEALTH = "HEALTH";
    private static final String FAMILY_FLOATER = "FAMILY_FLOATER";

    private final InsuranceRepository insuranceRepository;
    private final InsuranceCoveredMemberRepository coveredMemberRepository;
    private final InsuranceActivityEvaluator activityEvaluator;

    public HealthCoverageService(InsuranceRepository insuranceRepository,
                                  InsuranceCoveredMemberRepository coveredMemberRepository,
                                  InsuranceActivityEvaluator activityEvaluator) {
        this.insuranceRepository = insuranceRepository;
        this.coveredMemberRepository = coveredMemberRepository;
        this.activityEvaluator = activityEvaluator;
    }

    public record Result(BigDecimal individualCoverage, BigDecimal sharedCoverage, int activePolicyCount,
                          String status) {
    }

    public Result compute(Member member) {
        List<Insurance> ownedActiveHealth = insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                .stream()
                .filter(this::isHealth)
                .filter(activityEvaluator::isActive)
                .toList();

        BigDecimal individualCoverage = ownedActiveHealth.stream()
                .filter(p -> !isFamilyFloater(p))
                .map(this::coverageAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Every distinct active HEALTH family-floater policy that benefits this member: either they
        // own it, or they're explicitly listed as a covered member on someone else's policy. Each
        // policy's sum insured is counted exactly once, never once per covered member.
        Map<UUID, Insurance> floaterPoliciesCoveringMember = new LinkedHashMap<>();
        ownedActiveHealth.stream().filter(this::isFamilyFloater)
                .forEach(p -> floaterPoliciesCoveringMember.put(p.getId(), p));

        List<Insurance> coveredElsewhere = coveredMemberRepository.findByMemberId(member.getId()).stream()
                .map(InsuranceCoveredMember::getInsurance)
                .filter(this::isHealth)
                .filter(activityEvaluator::isActive)
                .filter(this::isFamilyFloater)
                .toList();
        coveredElsewhere.forEach(p -> floaterPoliciesCoveringMember.putIfAbsent(p.getId(), p));

        BigDecimal sharedCoverage = floaterPoliciesCoveringMember.values().stream()
                .map(this::coverageAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<UUID> allRelevantPolicyIds = new LinkedHashSet<>();
        ownedActiveHealth.forEach(p -> allRelevantPolicyIds.add(p.getId()));
        coveredElsewhere.forEach(p -> allRelevantPolicyIds.add(p.getId()));

        BigDecimal total = individualCoverage.add(sharedCoverage);
        // DETECTED/ADEQUATE are never auto-assigned in v1 - see HealthCoverageStatus.
        String status = total.compareTo(BigDecimal.ZERO) == 0
                ? HealthCoverageStatus.NO_COVER
                : HealthCoverageStatus.REVIEW_RECOMMENDED;

        return new Result(individualCoverage, sharedCoverage, allRelevantPolicyIds.size(), status);
    }

    private boolean isHealth(Insurance policy) {
        return policy.getInsuranceType() != null
                && policy.getInsuranceType().toUpperCase(Locale.ROOT).contains(HEALTH);
    }

    private boolean isFamilyFloater(Insurance policy) {
        return FAMILY_FLOATER.equalsIgnoreCase(policy.getPolicyType());
    }

    private BigDecimal coverageAmount(Insurance policy) {
        if (policy.getSumInsured() != null) {
            return policy.getSumInsured();
        }
        return policy.getSumAssured() != null ? policy.getSumAssured() : BigDecimal.ZERO;
    }
}
