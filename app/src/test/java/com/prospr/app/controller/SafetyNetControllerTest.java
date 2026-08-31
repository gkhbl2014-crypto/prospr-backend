package com.prospr.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.prospr.app.dto.response.SafetyNetResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;
import com.prospr.app.exception.ResourceNotFoundException;
import com.prospr.app.repository.MemberRepository;
import com.prospr.app.service.SafetyNetService;

@ExtendWith(MockitoExtension.class)
class SafetyNetControllerTest {

    @Mock
    private SafetyNetService safetyNetService;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private Authentication authentication;

    private SafetyNetController controller() {
        return new SafetyNetController(safetyNetService, memberRepository);
    }

    private Member member(Family family) {
        return Member.builder().id(UUID.randomUUID()).email("member@example.com").family(family).build();
    }

    @Test
    void selfAccessReturnsOwnSummary() {
        Member caller = member(null);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        SafetyNetResponse response = SafetyNetResponse.builder().build();
        when(safetyNetService.getForMember(caller)).thenReturn(response);

        ResponseEntity<SafetyNetResponse> result = controller().getOwnSafetyNet(authentication);

        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void familyMemberAccessReturnsSummary() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member caller = member(family);
        Member target = member(family);
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(memberRepository.findByIdAndFamilyId(target.getId(), family.getId())).thenReturn(Optional.of(target));
        SafetyNetResponse response = SafetyNetResponse.builder().build();
        when(safetyNetService.getForMember(target)).thenReturn(response);

        ResponseEntity<SafetyNetResponse> result = controller().getMemberSafetyNet(target.getId(), authentication);

        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void nonFamilyMemberAccessIsRejected() {
        Family family = Family.builder().id(UUID.randomUUID()).build();
        Member caller = member(family);
        UUID strangerId = UUID.randomUUID();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));
        when(memberRepository.findByIdAndFamilyId(strangerId, family.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller().getMemberSafetyNet(strangerId, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void callerWithNoFamilyCannotAccessOtherMembers() {
        Member caller = member(null);
        UUID otherId = UUID.randomUUID();
        when(authentication.getName()).thenReturn(caller.getEmail());
        when(memberRepository.findByEmail(caller.getEmail())).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> controller().getMemberSafetyNet(otherId, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
