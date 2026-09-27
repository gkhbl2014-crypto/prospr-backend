package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import com.prospr.app.dto.request.GoalRequest;
import com.prospr.app.dto.response.GoalResponse;
import com.prospr.app.entity.Goal;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.GoalValidationException;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.GoalRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.GoalStatus;
import com.prospr.app.service.GoalType;

@ExtendWith(MockitoExtension.class)
class GoalControllerTest {

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private Authentication authentication;

    private GoalController controller() {
        return new GoalController(goalRepository, memberRepository);
    }

    private Member member() {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").build();
    }

    private GoalRequest request(String goalType) {
        GoalRequest request = new GoalRequest();
        request.setGoalName("Marriage");
        request.setGoalType(goalType);
        request.setTargetAmount(new BigDecimal("1000000"));
        request.setCurrentAmount(new BigDecimal("250000"));
        request.setMonthlyContribution(new BigDecimal("25000"));
        request.setTargetDate(LocalDate.of(2028, 12, 1));
        return request;
    }

    @Test
    void listGoalsReturnsOnlyTheCallersGoalsWithComputedProgress() {
        Member caller = member();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        Goal goal = Goal.builder().id(UUID.randomUUID()).member(caller).goalName("Marriage")
                .goalType(GoalType.MARRIAGE).targetAmount(new BigDecimal("1000000"))
                .currentAmount(new BigDecimal("250000")).monthlyContribution(new BigDecimal("25000"))
                .targetDate(LocalDate.now().plusMonths(12)).build();
        when(goalRepository.findByMemberIdOrderByCreatedAtDesc(caller.getId())).thenReturn(List.of(goal));

        List<GoalResponse> result = controller().listGoals(authentication).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGoalName()).isEqualTo("Marriage");
        assertThat(result.get(0).getStatus()).isIn(GoalStatus.ON_TRACK, GoalStatus.NEEDS_ATTENTION);
        assertThat(result.get(0).getMessage()).isNotBlank();
    }

    @Test
    void createGoalPersistsItUnderTheCallerAndDefaultsOptionalAmountsToZero() {
        Member caller = member();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        GoalRequest request = request(GoalType.MARRIAGE);
        request.setCurrentAmount(null);
        request.setMonthlyContribution(null);
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller().createGoal(request, authentication);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isEqualTo(caller);
        assertThat(captor.getValue().getCurrentAmount()).isEqualByComparingTo("0");
        assertThat(captor.getValue().getMonthlyContribution()).isEqualByComparingTo("0");
    }

    @Test
    void createGoalRejectsAnUnrecognizedGoalType() {
        Member caller = member();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> controller().createGoal(request("SPACESHIP"), authentication))
                .isInstanceOf(GoalValidationException.class);
    }

    @Test
    void updateGoalRejectsSomeoneElsesGoal() {
        Member caller = member();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(goalRepository.findByIdAndMemberId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateGoal(UUID.randomUUID(), request(GoalType.MARRIAGE), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteGoalRemovesItForTheCaller() {
        Member caller = member();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        Goal goal = Goal.builder().id(UUID.randomUUID()).member(caller).build();
        when(goalRepository.findByIdAndMemberId(goal.getId(), caller.getId())).thenReturn(Optional.of(goal));

        controller().deleteGoal(goal.getId(), authentication);

        verify(goalRepository).delete(goal);
    }
}
