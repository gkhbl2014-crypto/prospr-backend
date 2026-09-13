package com.prospr.app.controller;

import java.math.BigDecimal;
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

import com.prospr.app.dto.request.FinancialGoalRequest;
import com.prospr.app.dto.response.FinancialGoalResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.FinancialGoal;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.repository.FinancialGoalRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.SafetyNetService;

@RestController
@RequestMapping("/api/financial-goal")
public class FinancialGoalController {

    private static final String SETU_SOURCE = "SETU";
    private static final String MANUAL_SOURCE = "MANUAL";

    private final FinancialGoalRepository financialGoalRepository;
    private final MemberRepository memberRepository;
    private final SafetyNetService safetyNetService;

    public FinancialGoalController(FinancialGoalRepository financialGoalRepository,
                                    MemberRepository memberRepository,
                                    SafetyNetService safetyNetService) {
        this.financialGoalRepository = financialGoalRepository;
        this.memberRepository = memberRepository;
        this.safetyNetService = safetyNetService;
    }

    @GetMapping
    public ResponseEntity<List<FinancialGoalResponse>> listGoals(Authentication authentication) {
        Member member = resolveMember(authentication);
        Family family = member.getFamily();

        List<FinancialGoalResponse> goals = (family == null
                ? financialGoalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId())
                : financialGoalRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(goals);
    }

    @PostMapping
    public ResponseEntity<FinancialGoalResponse> createGoal(@Valid @RequestBody FinancialGoalRequest request,
                                                              Authentication authentication) {
        Member member = resolveMember(authentication);

        FinancialGoal goal = FinancialGoal.builder()
                .member(member)
                .source(MANUAL_SOURCE)
                .currentAmount(BigDecimal.ZERO)
                .build();
        applyRequest(goal, request);
        goal = financialGoalRepository.save(goal);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FinancialGoalResponse> updateGoal(@PathVariable UUID id,
                                                              @Valid @RequestBody FinancialGoalRequest request,
                                                              Authentication authentication) {
        Member member = resolveMember(authentication);
        FinancialGoal goal = financialGoalRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial goal not found"));
        rejectIfSetuSourced(goal);

        applyRequest(goal, request);
        goal = financialGoalRepository.save(goal);

        safetyNetService.recomputeForMember(member);
        return ResponseEntity.ok(toResponse(goal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable UUID id, Authentication authentication) {
        Member member = resolveMember(authentication);
        FinancialGoal goal = financialGoalRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Financial goal not found"));
        rejectIfSetuSourced(goal);

        financialGoalRepository.delete(goal);
        safetyNetService.recomputeForMember(member);
        return ResponseEntity.noContent().build();
    }

    private void rejectIfSetuSourced(FinancialGoal goal) {
        if (SETU_SOURCE.equals(goal.getSource())) {
            throw new SafetyNetConflictException(
                    "This goal was linked through Account Aggregator and can't be edited manually");
        }
    }

    private void applyRequest(FinancialGoal goal, FinancialGoalRequest request) {
        goal.setGoalName(request.getGoalName());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setCurrentAmount(request.getCurrentAmount() != null ? request.getCurrentAmount() : BigDecimal.ZERO);
        goal.setTargetDate(request.getTargetDate());
    }

    private FinancialGoalResponse toResponse(FinancialGoal goal) {
        return FinancialGoalResponse.builder()
                .id(goal.getId())
                .memberId(goal.getMember().getId())
                .goalName(goal.getGoalName())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .targetDate(goal.getTargetDate())
                .source(goal.getSource())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
