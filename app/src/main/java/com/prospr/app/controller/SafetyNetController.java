package com.prospr.app.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.response.SafetyNetResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.SafetyNetService;

@RestController
@RequestMapping("/api/safety-net")
public class SafetyNetController {

    private final SafetyNetService safetyNetService;
    private final MemberRepository memberRepository;

    public SafetyNetController(SafetyNetService safetyNetService, MemberRepository memberRepository) {
        this.safetyNetService = safetyNetService;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<SafetyNetResponse> getOwnSafetyNet(Authentication authentication) {
        Member member = resolveMember(authentication);
        return ResponseEntity.ok(safetyNetService.getForMember(member));
    }

    /**
     * Family-wide read, matching the precedent already set for transactions/insurance/mutual-funds:
     * any member of the same family can view another member's Safety Net. Every Safety Net WRITE
     * endpoint, by contrast, stays self-only - this asymmetry is intentional.
     */
    @GetMapping("/{memberId}")
    public ResponseEntity<SafetyNetResponse> getMemberSafetyNet(@PathVariable UUID memberId,
                                                                  Authentication authentication) {
        Member caller = resolveMember(authentication);
        if (memberId.equals(caller.getId())) {
            return ResponseEntity.ok(safetyNetService.getForMember(caller));
        }

        Family family = caller.getFamily();
        if (family == null) {
            throw new ResourceNotFoundException("Member not found in your household");
        }
        Member target = memberRepository.findByIdAndFamilyId(memberId, family.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in your household"));
        return ResponseEntity.ok(safetyNetService.getForMember(target));
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
