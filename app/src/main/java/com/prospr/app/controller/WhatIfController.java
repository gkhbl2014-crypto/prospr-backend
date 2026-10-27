package com.prospr.app.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.request.WhatIfRequest;
import com.prospr.app.dto.response.WhatIfResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.WhatIfService;

/** "Can I afford this?" - computed on the spot from the caller's own data, never stored. */
@RestController
@RequestMapping("/api/what-if")
public class WhatIfController {

    private final WhatIfService whatIfService;
    private final MemberRepository memberRepository;

    public WhatIfController(WhatIfService whatIfService, MemberRepository memberRepository) {
        this.whatIfService = whatIfService;
        this.memberRepository = memberRepository;
    }

    /** POST because the question carries parameters; it changes nothing. */
    @PostMapping
    public ResponseEntity<WhatIfResponse> evaluate(@Valid @RequestBody WhatIfRequest request,
                                                    Authentication authentication) {
        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
        return ResponseEntity.ok(whatIfService.evaluate(member, request));
    }
}
