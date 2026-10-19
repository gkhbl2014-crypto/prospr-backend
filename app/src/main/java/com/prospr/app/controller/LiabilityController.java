package com.prospr.app.controller;

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

import com.prospr.app.dto.request.LiabilityRequest;
import com.prospr.app.dto.response.LiabilityResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Liability;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.repository.LiabilityRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.SafetyNetService;

@RestController
@RequestMapping("/api/liability")
public class LiabilityController {

    private static final String SETU_SOURCE = "SETU";
    private static final String MANUAL_SOURCE = "MANUAL";

    private final LiabilityRepository liabilityRepository;
    private final MemberRepository memberRepository;
    private final SafetyNetService safetyNetService;

    public LiabilityController(LiabilityRepository liabilityRepository,
                                MemberRepository memberRepository,
                                SafetyNetService safetyNetService) {
        this.liabilityRepository = liabilityRepository;
        this.memberRepository = memberRepository;
        this.safetyNetService = safetyNetService;
    }

    @GetMapping
    public ResponseEntity<List<LiabilityResponse>> listLiabilities(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();

        List<LiabilityResponse> liabilities = (family == null
                ? liabilityRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                : liabilityRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(liabilities);
    }

    @PostMapping
    public ResponseEntity<LiabilityResponse> createLiability(@Valid @RequestBody LiabilityRequest request,
                                                               Authentication authentication) {
        Member member = resolveMember(authentication);

        Liability liability = Liability.builder()
                .member(member)
                .source(MANUAL_SOURCE)
                .build();
        applyRequest(liability, request);
        liability = liabilityRepository.save(liability);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(liability));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LiabilityResponse> updateLiability(@PathVariable UUID id,
                                                               @Valid @RequestBody LiabilityRequest request,
                                                               Authentication authentication) {
        Member member = resolveMember(authentication);
        Liability liability = liabilityRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Liability not found"));
        rejectIfSetuSourced(liability);

        applyRequest(liability, request);
        liability = liabilityRepository.save(liability);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.ok(toResponse(liability));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLiability(@PathVariable UUID id, Authentication authentication) {
        Member member = resolveMember(authentication);
        Liability liability = liabilityRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Liability not found"));
        rejectIfSetuSourced(liability);

        liabilityRepository.delete(liability);
        safetyNetService.recomputeForMember(member);
        return ResponseEntity.noContent().build();
    }

    private void rejectIfSetuSourced(Liability liability) {
        if (SETU_SOURCE.equals(liability.getSource())) {
            throw new SafetyNetConflictException(
                    "This liability was linked through Account Aggregator and can't be edited manually");
        }
    }

    private void applyRequest(Liability liability, LiabilityRequest request) {
        liability.setLoanType(request.getLoanType());
        liability.setLenderName(request.getLenderName());
        liability.setOutstandingAmount(request.getOutstandingAmount());
        liability.setEmiAmount(request.getEmiAmount());
        liability.setInterestRate(request.getInterestRate());
        liability.setStartDate(request.getStartDate());
        liability.setEndDate(request.getEndDate());
    }

    private LiabilityResponse toResponse(Liability liability) {
        return LiabilityResponse.builder()
                .id(liability.getId())
                .memberId(liability.getMember().getId())
                .loanType(liability.getLoanType())
                .lenderName(liability.getLenderName())
                .outstandingAmount(liability.getOutstandingAmount())
                .emiAmount(liability.getEmiAmount())
                .interestRate(liability.getInterestRate())
                .startDate(liability.getStartDate())
                .endDate(liability.getEndDate())
                .source(liability.getSource())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
