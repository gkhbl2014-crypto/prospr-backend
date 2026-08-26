package com.prospr.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.response.InsuranceResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.InsuranceRepository;
import com.prospr.app.repository.MemberRepository;

@RestController
@RequestMapping("/api/insurance")
public class InsuranceController {

    private final InsuranceRepository insuranceRepository;
    private final MemberRepository memberRepository;

    public InsuranceController(InsuranceRepository insuranceRepository, MemberRepository memberRepository) {
        this.insuranceRepository = insuranceRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<List<InsuranceResponse>> listInsurance(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();

        List<InsuranceResponse> policies = (family == null
                ? insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                : insuranceRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .stream()
                .map(policy -> InsuranceResponse.builder()
                        .id(policy.getId())
                        .maskedPolicyNumber(policy.getMaskedPolicyNumber())
                        .insuranceType(policy.getInsuranceType())
                        .policyNumber(policy.getPolicyNumber())
                        .insurerName(policy.getInsurerName())
                        .policyName(policy.getPolicyName())
                        .sumAssured(policy.getSumAssured())
                        .premiumAmount(policy.getPremiumAmount())
                        .premiumFrequency(policy.getPremiumFrequency())
                        .policyStartDate(policy.getPolicyStartDate())
                        .policyEndDate(policy.getPolicyEndDate())
                        .maturityDate(policy.getMaturityDate())
                        .nextPremiumDueDate(policy.getNextPremiumDueDate())
                        .policyStatus(policy.getPolicyStatus())
                        .nomineeName(policy.getNomineeName())
                        .build())
                .toList();

        return ResponseEntity.ok(policies);
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
