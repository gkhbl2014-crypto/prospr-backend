package com.prospr.app.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prospr.app.dto.response.ActionableInsightResponse;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.ActionableInsightService;
import com.prospr.app.service.CurrentMonthResolver;

/** Self-only: up to 3 "what could I do about it" insights, computed fresh from the caller's own
 *  Lifestyle Creep baselines and Goals - see {@link ActionableInsightService} for the calculation. */
@RestController
@RequestMapping("/api/insights")
public class ActionableInsightController {

    private final ActionableInsightService actionableInsightService;
    private final MemberRepository memberRepository;
    private final CurrentMonthResolver currentMonthResolver;

    public ActionableInsightController(ActionableInsightService actionableInsightService,
                                        MemberRepository memberRepository,
                                        CurrentMonthResolver currentMonthResolver) {
        this.actionableInsightService = actionableInsightService;
        this.memberRepository = memberRepository;
        this.currentMonthResolver = currentMonthResolver;
    }

    @GetMapping("/actionable")
    public ResponseEntity<List<ActionableInsightResponse>> actionable(Authentication authentication) {
        Member member = resolveMember(authentication);
        CurrentMonthResolver.EffectivePeriod period = currentMonthResolver.resolve(member.getId());
        List<ActionableInsightResponse> insights = actionableInsightService
                .buildInsights(member, period.month(), period.asOfDate(), LocalDate.now()).stream()
                .map(i -> ActionableInsightResponse.builder()
                        .type(i.type())
                        .label(i.label())
                        .message(i.message())
                        .monthlyImpact(i.monthlyImpact())
                        .build())
                .toList();
        return ResponseEntity.ok(insights);
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByAuthSubject(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
