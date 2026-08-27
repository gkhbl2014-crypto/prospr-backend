package com.prospr.app.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.UpdateFamilyNameRequest;
import com.prospr.app.dto.response.FamilyResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.FamilyRepository;
import com.prospr.app.repository.MemberRepository;

@RestController
@RequestMapping("/api/family")
public class FamilyController {

    private final FamilyRepository familyRepository;
    private final MemberRepository memberRepository;

    public FamilyController(FamilyRepository familyRepository, MemberRepository memberRepository) {
        this.familyRepository = familyRepository;
        this.memberRepository = memberRepository;
    }

    /** Any member of the household can rename it - the new name is shared, not per-member. */
    @PatchMapping
    public ResponseEntity<FamilyResponse> updateFamilyName(@Valid @RequestBody UpdateFamilyNameRequest request,
                                                             Authentication authentication) {
        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
        Family family = member.getFamily();
        if (family == null) {
            throw new ResourceNotFoundException("You're not part of a family yet");
        }

        family.setFamilyName(request.getFamilyName().trim());
        familyRepository.save(family);

        return ResponseEntity.ok(FamilyResponse.builder()
                .id(family.getId())
                .familyName(family.getFamilyName())
                .build());
    }
}
