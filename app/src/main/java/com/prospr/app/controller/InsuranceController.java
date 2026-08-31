package com.prospr.app.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.InsurancePolicyRequest;
import com.prospr.app.dto.response.InsuranceResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Insurance;
import com.prospr.app.entity.InsuranceCoveredMember;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.exception.SafetyNetValidationException;
import com.prospr.app.repository.InsuranceCoveredMemberRepository;
import com.prospr.app.repository.InsuranceRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.SafetyNetService;

@RestController
@RequestMapping("/api/insurance")
public class InsuranceController {

    private static final String SETU_SOURCE = "SETU";
    private static final String MANUAL_SOURCE = "MANUAL";

    private final InsuranceRepository insuranceRepository;
    private final InsuranceCoveredMemberRepository coveredMemberRepository;
    private final MemberRepository memberRepository;
    private final SafetyNetService safetyNetService;

    public InsuranceController(InsuranceRepository insuranceRepository,
                                InsuranceCoveredMemberRepository coveredMemberRepository,
                                MemberRepository memberRepository,
                                SafetyNetService safetyNetService) {
        this.insuranceRepository = insuranceRepository;
        this.coveredMemberRepository = coveredMemberRepository;
        this.memberRepository = memberRepository;
        this.safetyNetService = safetyNetService;
    }

    @GetMapping
    public ResponseEntity<List<InsuranceResponse>> listInsurance(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();

        List<InsuranceResponse> policies = (family == null
                ? insuranceRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                : insuranceRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(policies);
    }

    /** MANUAL policies only - Setu-synced policies arrive automatically through the AA consent flow. */
    @PostMapping
    public ResponseEntity<InsuranceResponse> createPolicy(@Valid @RequestBody InsurancePolicyRequest request,
                                                            Authentication authentication) {
        Member member = resolveMember(authentication);

        Insurance policy = Insurance.builder()
                .member(member)
                .source(MANUAL_SOURCE)
                .lastUpdated(LocalDateTime.now())
                .build();
        applyRequest(policy, request);
        policy = insuranceRepository.save(policy);
        applyCoveredMembers(policy, request.getCoveredMemberIds(), member);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(policy));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InsuranceResponse> updatePolicy(@PathVariable UUID id,
                                                            @Valid @RequestBody InsurancePolicyRequest request,
                                                            Authentication authentication) {
        Member member = resolveMember(authentication);
        Insurance policy = insuranceRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Insurance policy not found"));
        rejectIfSetuSourced(policy);

        applyRequest(policy, request);
        policy.setLastUpdated(LocalDateTime.now());
        policy = insuranceRepository.save(policy);
        applyCoveredMembers(policy, request.getCoveredMemberIds(), member);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.ok(toResponse(policy));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePolicy(@PathVariable UUID id, Authentication authentication) {
        Member member = resolveMember(authentication);
        Insurance policy = insuranceRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Insurance policy not found"));
        rejectIfSetuSourced(policy);

        insuranceRepository.delete(policy);
        safetyNetService.recomputeForMember(member);
        return ResponseEntity.noContent().build();
    }

    private void rejectIfSetuSourced(Insurance policy) {
        if (SETU_SOURCE.equals(policy.getSource())) {
            throw new SafetyNetConflictException(
                    "This policy was linked through Account Aggregator and can't be edited manually");
        }
    }

    private void applyRequest(Insurance policy, InsurancePolicyRequest request) {
        policy.setInsuranceType(request.getInsuranceType());
        policy.setPolicyType(request.getPolicyType());
        policy.setPolicyNumber(request.getPolicyNumber());
        policy.setInsurerName(request.getInsurerName());
        policy.setPolicyName(request.getPolicyName());
        policy.setSumInsured(request.getSumInsured());
        policy.setSumAssured(request.getSumAssured());
        policy.setPremiumAmount(request.getPremiumAmount());
        policy.setPremiumFrequency(request.getPremiumFrequency());
        policy.setPolicyStartDate(request.getPolicyStartDate());
        policy.setPolicyEndDate(request.getPolicyEndDate());
        policy.setMaturityDate(request.getMaturityDate());
        policy.setNextPremiumDueDate(request.getNextPremiumDueDate());
        policy.setPolicyStatus(request.getPolicyStatus() == null || request.getPolicyStatus().isBlank()
                ? "ACTIVE" : request.getPolicyStatus());
        policy.setNomineeName(request.getNomineeName());
    }

    /** Full-replace semantics: clears any existing covered-member rows, then re-inserts the given set. */
    private void applyCoveredMembers(Insurance policy, List<UUID> coveredMemberIds, Member caller) {
        coveredMemberRepository.deleteByInsuranceId(policy.getId());
        if (coveredMemberIds == null || coveredMemberIds.isEmpty()) {
            return;
        }
        Family family = caller.getFamily();
        if (family == null) {
            throw new SafetyNetValidationException("You must be part of a family to add covered members");
        }
        for (UUID coveredMemberId : coveredMemberIds) {
            Member covered = memberRepository.findByIdAndFamilyId(coveredMemberId, family.getId())
                    .orElseThrow(() -> new SafetyNetValidationException("Covered member is not part of your family"));
            coveredMemberRepository.save(InsuranceCoveredMember.builder()
                    .insurance(policy)
                    .member(covered)
                    .build());
        }
    }

    private InsuranceResponse toResponse(Insurance policy) {
        List<UUID> coveredMemberIds = coveredMemberRepository.findByInsuranceId(policy.getId()).stream()
                .map(icm -> icm.getMember().getId())
                .toList();

        return InsuranceResponse.builder()
                .id(policy.getId())
                .memberId(policy.getMember().getId())
                .maskedPolicyNumber(policy.getMaskedPolicyNumber())
                .insuranceType(policy.getInsuranceType())
                .policyType(policy.getPolicyType())
                .policyNumber(policy.getPolicyNumber())
                .insurerName(policy.getInsurerName())
                .policyName(policy.getPolicyName())
                .sumAssured(policy.getSumAssured())
                .sumInsured(policy.getSumInsured())
                .premiumAmount(policy.getPremiumAmount())
                .premiumFrequency(policy.getPremiumFrequency())
                .policyStartDate(policy.getPolicyStartDate())
                .policyEndDate(policy.getPolicyEndDate())
                .maturityDate(policy.getMaturityDate())
                .nextPremiumDueDate(policy.getNextPremiumDueDate())
                .policyStatus(policy.getPolicyStatus())
                .nomineeName(policy.getNomineeName())
                .source(policy.getSource())
                .coveredMemberIds(coveredMemberIds)
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
