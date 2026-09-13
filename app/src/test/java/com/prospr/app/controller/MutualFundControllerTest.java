package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

import com.prospr.app.dto.request.MutualFundHoldingRequest;
import com.prospr.app.dto.response.MutualFundHoldingResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.entity.MutualFundHolding;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.repository.MutualFundHoldingRepository;
import com.prospr.app.service.SafetyNetService;

@ExtendWith(MockitoExtension.class)
class MutualFundControllerTest {

    @Mock
    private MutualFundHoldingRepository mutualFundHoldingRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SafetyNetService safetyNetService;
    @Mock
    private Authentication authentication;

    private MutualFundController controller() {
        return new MutualFundController(mutualFundHoldingRepository, memberRepository, safetyNetService);
    }

    private Member member(Family family) {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").family(family).build();
    }

    private void stubCaller(Member member) {
        when(authentication.getName()).thenReturn(member.getEmail());
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    private MutualFundHoldingRequest request() {
        MutualFundHoldingRequest request = new MutualFundHoldingRequest();
        request.setAmc("Test AMC");
        request.setCostValue(new BigDecimal("10000"));
        return request;
    }

    @Test
    void createSetsManualSourceAndTriggersRecompute() {
        Member member = member(null);
        stubCaller(member);
        when(mutualFundHoldingRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MutualFundHoldingResponse response = controller().createHolding(request(), authentication).getBody();

        assertThat(response.getSource()).isEqualTo("MANUAL");
        org.mockito.Mockito.verify(safetyNetService).recomputeForMember(member);
    }

    @Test
    void updateRejectsSetuSourcedHoldingWith409() {
        Member member = member(null);
        stubCaller(member);
        MutualFundHolding setuHolding = MutualFundHolding.builder().id(UUID.randomUUID()).member(member).source("SETU").build();
        when(mutualFundHoldingRepository.findByIdAndMemberId(setuHolding.getId(), member.getId()))
                .thenReturn(Optional.of(setuHolding));

        assertThatThrownBy(() -> controller().updateHolding(setuHolding.getId(), request(), authentication))
                .isInstanceOf(SafetyNetConflictException.class);
    }

    @Test
    void updateSelfOnlyRejectsAnotherMembersHolding() {
        Member member = member(null);
        stubCaller(member);
        UUID otherHoldingId = UUID.randomUUID();
        when(mutualFundHoldingRepository.findByIdAndMemberId(otherHoldingId, member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateHolding(otherHoldingId, request(), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRejectsSetuSourcedHoldingWith409() {
        Member member = member(null);
        stubCaller(member);
        MutualFundHolding setuHolding = MutualFundHolding.builder().id(UUID.randomUUID()).member(member).source("SETU").build();
        when(mutualFundHoldingRepository.findByIdAndMemberId(setuHolding.getId(), member.getId()))
                .thenReturn(Optional.of(setuHolding));

        assertThatThrownBy(() -> controller().deleteHolding(setuHolding.getId(), authentication))
                .isInstanceOf(SafetyNetConflictException.class);
    }

    @Test
    void listReturnsFamilyWideWhenFamilyPresent() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member member = member(family);
        stubCaller(member);
        when(mutualFundHoldingRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .thenReturn(List.of(MutualFundHolding.builder().id(UUID.randomUUID()).member(member).source("SETU").build()));

        List<MutualFundHoldingResponse> result = controller().listHoldings(authentication).getBody();

        assertThat(result).hasSize(1);
    }

    @Test
    void listReturnsMemberOnlyWhenNoFamily() {
        Member member = member(null);
        stubCaller(member);
        when(mutualFundHoldingRepository.findByMemberIdOrderByCreatedAtDesc(member.getId()))
                .thenReturn(List.of(MutualFundHolding.builder().id(UUID.randomUUID()).member(member).source("MANUAL").build()));

        List<MutualFundHoldingResponse> result = controller().listHoldings(authentication).getBody();

        assertThat(result).hasSize(1);
    }
}
