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

import com.prospr.app.dto.request.LiabilityRequest;
import com.prospr.app.dto.response.LiabilityResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Liability;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.exception.SafetyNetConflictException;
import com.prospr.app.repository.LiabilityRepository;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.SafetyNetService;

@ExtendWith(MockitoExtension.class)
class LiabilityControllerTest {

    @Mock
    private LiabilityRepository liabilityRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private SafetyNetService safetyNetService;
    @Mock
    private Authentication authentication;

    private LiabilityController controller() {
        return new LiabilityController(liabilityRepository, memberRepository, safetyNetService);
    }

    private Member member(Family family) {
        return Member.builder().id(UUID.randomUUID()).email("caller@example.com").family(family).build();
    }

    private void stubCaller(Member member) {
        when(authentication.getName()).thenReturn(member.getEmail());
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));
    }

    private LiabilityRequest request() {
        LiabilityRequest request = new LiabilityRequest();
        request.setLoanType("HOME");
        request.setOutstandingAmount(new BigDecimal("2000000"));
        return request;
    }

    @Test
    void createPersistsWithManualSourceAndTriggersRecompute() {
        Member member = member(null);
        stubCaller(member);
        when(liabilityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LiabilityResponse response = controller().createLiability(request(), authentication).getBody();

        assertThat(response.getSource()).isEqualTo("MANUAL");
        verify(safetyNetService).recomputeForMember(member);
    }

    @Test
    void updateIsScopedToOwnRecordViaFindByIdAndMemberId() {
        Member member = member(null);
        stubCaller(member);
        UUID otherId = UUID.randomUUID();
        when(liabilityRepository.findByIdAndMemberId(otherId, member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().updateLiability(otherId, request(), authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateRejectsSetuSourcedRecordWith409() {
        Member member = member(null);
        stubCaller(member);
        Liability setuRow = Liability.builder().id(UUID.randomUUID()).member(member).source("SETU").build();
        when(liabilityRepository.findByIdAndMemberId(setuRow.getId(), member.getId())).thenReturn(Optional.of(setuRow));

        assertThatThrownBy(() -> controller().updateLiability(setuRow.getId(), request(), authentication))
                .isInstanceOf(SafetyNetConflictException.class);
    }

    @Test
    void deleteIsScopedToOwnRecord() {
        Member member = member(null);
        stubCaller(member);
        Liability own = Liability.builder().id(UUID.randomUUID()).member(member).source("MANUAL").build();
        when(liabilityRepository.findByIdAndMemberId(own.getId(), member.getId())).thenReturn(Optional.of(own));

        controller().deleteLiability(own.getId(), authentication);

        verify(liabilityRepository).delete(own);
        verify(safetyNetService).recomputeForMember(member);
    }

    @Test
    void listReturnsFamilyWideWhenFamilyPresent() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member member = member(family);
        stubCaller(member);
        when(liabilityRepository.findByMemberFamilyIdOrderByCreatedAtDesc(family.getId()))
                .thenReturn(List.of(Liability.builder().id(UUID.randomUUID()).member(member).source("MANUAL").build()));

        assertThat(controller().listLiabilities(authentication).getBody()).hasSize(1);
    }
}
