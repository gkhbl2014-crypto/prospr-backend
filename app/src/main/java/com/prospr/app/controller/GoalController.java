package com.prospr.app.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.prospr.app.dto.request.GoalRequest;
import com.prospr.app.dto.response.GoalResponse;
import com.prospr.app.entity.Goal;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.GoalValidationException;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.GoalRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.GoalCalculator;
import com.prospr.app.service.GoalType;

/**
 * Self-only CRUD for savings goals ("Marriage", target ₹10L by Dec 2028). Progress/status/message are
 * computed fresh on every read by {@link GoalCalculator} - nothing derived is persisted, so there's
 * nothing to keep in sync when "today" moves forward a day.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalRepository goalRepository;
    private final MemberRepository memberRepository;

    public GoalController(GoalRepository goalRepository, MemberRepository memberRepository) {
        this.goalRepository = goalRepository;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> listGoals(Authentication authentication) {
        Member member = resolveMember(authentication);
        List<GoalResponse> goals = goalRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(goals);
    }

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(@Valid @RequestBody GoalRequest request,
                                                    Authentication authentication) {
        Member member = resolveMember(authentication);
        validateGoalType(request.getGoalType());

        Goal goal = Goal.builder().member(member).build();
        applyRequest(goal, request);
        goal = goalRepository.save(goal);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(goal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(@PathVariable UUID id,
                                                    @Valid @RequestBody GoalRequest request,
                                                    Authentication authentication) {
        Member member = resolveMember(authentication);
        Goal goal = goalRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));
        validateGoalType(request.getGoalType());

        applyRequest(goal, request);
        goal = goalRepository.save(goal);

        return ResponseEntity.ok(toResponse(goal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable UUID id, Authentication authentication) {
        Member member = resolveMember(authentication);
        Goal goal = goalRepository.findByIdAndMemberId(id, member.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));

        goalRepository.delete(goal);
        return ResponseEntity.noContent().build();
    }

    private void validateGoalType(String goalType) {
        if (!GoalType.ALL.contains(goalType)) {
            throw new GoalValidationException(
                    "Unrecognized goal type '" + goalType + "' - expected one of " + String.join(", ", GoalType.ALL));
        }
    }

    private void applyRequest(Goal goal, GoalRequest request) {
        goal.setGoalName(request.getGoalName());
        goal.setGoalType(request.getGoalType());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setCurrentAmount(request.getCurrentAmount() != null ? request.getCurrentAmount() : BigDecimal.ZERO);
        goal.setMonthlyContribution(request.getMonthlyContribution() != null ? request.getMonthlyContribution() : BigDecimal.ZERO);
        goal.setTargetDate(request.getTargetDate());
    }

    private GoalResponse toResponse(Goal goal) {
        GoalCalculator.Progress progress = GoalCalculator.evaluate(goal.getTargetAmount(), goal.getCurrentAmount(),
                goal.getMonthlyContribution(), goal.getTargetDate(), LocalDate.now());

        return GoalResponse.builder()
                .id(goal.getId())
                .goalName(goal.getGoalName())
                .goalType(goal.getGoalType())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .monthlyContribution(goal.getMonthlyContribution())
                .targetDate(goal.getTargetDate())
                .progressPercent(progress.progressPercent())
                .remainingAmount(progress.remainingAmount())
                .monthsRemaining(progress.monthsRemaining())
                .requiredMonthlyContribution(progress.requiredMonthlyContribution())
                .status(progress.status())
                .message(progress.message())
                .build();
    }

    private Member resolveMember(Authentication authentication) {
        return memberRepository.findByAuthSubject(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in member was not found"));
    }
}
