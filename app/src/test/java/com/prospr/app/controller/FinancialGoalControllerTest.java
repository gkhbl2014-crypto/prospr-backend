package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

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

@ExtendWith(MockitoExtension.class)
class FinancialGoalControllerTest {

    @Mock
    private FinancialGoalRepository financialGoalRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SafetyNetService safetyNetService;
    @Mock
    private Authentication authentication;

    private FinancialGoalController controller() {
        return new FinancialGoalController(financialGoalRepository, memberRepository, safetyNetService);
    }

    private Member member(Family family) {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").family(family).build();
    }

    private void stubCaller(Member member) {
        when(authentication.getName()).thenReturn(member.getEmail());
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    private FinancialGoalRequest request() {
        FinancialGoalRequest request = new FinancialGoalRequest();
        request.setGoalName("Emergency Trip Fund");
        request.setTargetAmount(new BigDecimal("100000"));
        return request;
    }

    @Test
    void createDefaultsCurrentAmountToZeroWhenOmittedAndTriggersRecompute() {
        Member member = member(null);
        stubCaller(member);
        when(financialGoalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FinancialGoalResponse response = controller().createGoal(request(), authentication).getBody();

        assertThat(response.getSource()).isEqualTo("MANUAL");
        assertThat(response.getCurrentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(safetyNetService).recomputeForMember(member);
    }

    @Test
    void updateIsScopedToOwnRecordViaFindByIdAndMemberId() {
        Member member = member(null);
        stubCaller(member);
        UUID otherId = UUID.randomUUID();
        when(financialGoalRepository.findByIdAndMemberId(otherId, member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateGoal(otherId, request(), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRejectsSetuSourcedRecordWith409() {
        Member member = member(null);
        stubCaller(member);
        FinancialGoal setuRow = FinancialGoal.builder().id(UUID.randomUUID()).member(member).source("SETU").build();
        when(financialGoalRepository.findByIdAndMemberId(setuRow.getId(), member.getId())).thenReturn(Optional.of(setuRow));

        assertThatThrownBy(() -> controller().updateGoal(setuRow.getId(), request(), authentication))
                .isInstanceOf(SafetyNetConflictException.class);
    }

    @Test
    void deleteIsScopedToOwnRecord() {
        Member member = member(null);
        stubCaller(member);
        FinancialGoal own = FinancialGoal.builder().id(UUID.randomUUID()).member(member).source("MANUAL").build();
        when(financialGoalRepository.findByIdAndMemberId(own.getId(), member.getId())).thenReturn(Optional.of(own));

        controller().deleteGoal(own.getId(), authentication);

        verify(financialGoalRepository).delete(own);
    }

    @Test
    void listReturnsFamilyWideWhenFamilyPresent() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member member = member(family);
        stubCaller(member);
        when(financialGoalRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .thenReturn(List.of(FinancialGoal.builder().id(UUID.randomUUID()).member(member).source("MANUAL").build()));

        assertThat(controller().listGoals(authentication).getBody()).hasSize(1);
    }
}
