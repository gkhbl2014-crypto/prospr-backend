package com.prospr.app.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.CreateSessionRequest;
import com.prospr.app.dto.response.SetuConsentResponse;
import com.prospr.app.dto.response.SetuSessionResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.SetuConsentService;
import com.prospr.app.service.SetuSessionService;

@RestController
@RequestMapping("/api/setu")
public class SetuConsentController {

    private final SetuConsentService consentService;
    private final SetuSessionService sessionService;
    private final MemberRepository memberRepository;

    public SetuConsentController(SetuConsentService consentService, SetuSessionService sessionService,
                                  MemberRepository memberRepository) {
        this.consentService = consentService;
        this.sessionService = sessionService;
        this.memberRepository = memberRepository;
    }

    @PostMapping("/consent")
    public ResponseEntity<SetuConsentResponse> createConsent(Authentication authentication) {
        Member member = resolveMember(authentication);
        return ResponseEntity.ok(consentService.createConsent(member.getPhone()));
    }

    @PostMapping("/sessions")
    public ResponseEntity<SetuSessionResponse> createSession(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.ok(sessionService.createSession(request.getConsentId()));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<com.prospr.app.dto.setu.SetuSessionResponse> getSession(@PathVariable String sessionId,
                                                                                   Authentication authentication) {
        Member member = resolveMember(authentication);
        return ResponseEntity.ok(sessionService.fetchSession(sessionId, member));
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}