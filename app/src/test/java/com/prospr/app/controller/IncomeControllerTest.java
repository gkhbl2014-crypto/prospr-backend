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

import com.prospr.app.dto.request.IncomeRequest;
import com.prospr.app.dto.response.IncomeResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Income;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.repository.IncomeRepository;
import com.prospr.app.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class IncomeControllerTest {

    @Mock
    private IncomeRepository incomeRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private Authentication authentication;

    private IncomeController controller() {
        return new IncomeController(incomeRepository, memberRepository);
    }

    private Member member(Family family) {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").family(family).build();
    }

    private void stubCaller(Member member) {
        when(authentication.getName()).thenReturn(member.getEmail());
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    private IncomeRequest request() {
        IncomeRequest request = new IncomeRequest();
        request.setIncomeType("SALARY");
        request.setMonthlyAmount(new BigDecimal("80000"));
        request.setFrequency("MONTHLY");
        return request;
    }

    @Test
    void createPersistsWithManualSource() {
        Member member = member(null);
        stubCaller(member);
        when(incomeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IncomeResponse response = controller().createIncome(request(), authentication).getBody();

        assertThat(response.getSource()).isEqualTo("MANUAL");
        assertThat(response.getFrequency()).isEqualTo("MONTHLY");
    }

    @Test
    void updateIsScopedToOwnRecordViaFindByIdAndMemberId() {
        Member member = member(null);
        stubCaller(member);
        UUID otherId = UUID.randomUUID();
        when(incomeRepository.findByIdAndMemberId(otherId, member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateIncome(otherId, request(), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRejectsSetuSourcedRecordWith409() {
        Member member = member(null);
        stubCaller(member);
        Income setuRow = Income.builder().id(UUID.randomUUID()).member(member).source("SETU").build();
        when(incomeRepository.findByIdAndMemberId(setuRow.getId(), member.getId())).thenReturn(Optional.of(setuRow));

        assertThatThrownBy(() -> controller().updateIncome(setuRow.getId(), request(), authentication))
                .isInstanceOf(SafetyNetConflictException.class);
    }

    @Test
    void deleteIsScopedToOwnRecord() {
        Member member = member(null);
        stubCaller(member);
        Income own = Income.builder().id(UUID.randomUUID()).member(member).source("MANUAL").build();
        when(incomeRepository.findByIdAndMemberId(own.getId(), member.getId())).thenReturn(Optional.of(own));

        controller().deleteIncome(own.getId(), authentication);

        verify(incomeRepository).delete(own);
    }

    @Test
    void listReturnsFamilyWideWhenFamilyPresent() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member member = member(family);
        stubCaller(member);
        when(incomeRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .thenReturn(List.of(Income.builder().id(UUID.randomUUID()).member(member).source("MANUAL").build()));

        assertThat(controller().listIncome(authentication).getBody()).hasSize(1);
    }
}
