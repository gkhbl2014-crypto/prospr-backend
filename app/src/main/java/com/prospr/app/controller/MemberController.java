package com.prospr.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.response.FamilyMemberSummaryResponse;
import com.prospr.app.dto.response.MemberProfileResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberRepository memberRepository;

    public MemberController(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<MemberProfileResponse> getCurrentMember(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();

        return ResponseEntity.ok(MemberProfileResponse.builder()
                .id(member.getId())
                .familyId(family == null ? null : family.getId())
                .familyName(family == null ? null : family.getFamilyName())
                .familyInviteCode(family == null ? null : family.getInviteCode())
                .firstName(member.getFirstName())
                .lastName(member.getLastName())
                .email(member.getEmail())
                .role(member.getRole())
                .build());
    }

    /** The full household roster for the dashboard/family list - every member in the caller's family. */
    @GetMapping
    public ResponseEntity<List<FamilyMemberSummaryResponse>> listFamilyMembers(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();
        if (family == null) {
            return ResponseEntity.ok(List.of());
        }

        List<FamilyMemberSummaryResponse> members = memberRepository
                .findByFamilyIdOrderByCreatedAtAsc(family.getId()).stream()
                .map(m -> FamilyMemberSummaryResponse.builder()
                        .id(m.getId())
                        .firstName(m.getFirstName())
                        .lastName(m.getLastName())
                        .role(m.getRole())
                        .build())
                .toList();
        return ResponseEntity.ok(members);
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
