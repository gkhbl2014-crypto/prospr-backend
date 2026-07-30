package com.prospr.app.mapper;

import org.springframework.stereotype.Component;

import com.prospr.app.dto.request.RegisterRequest;
import com.prospr.app.dto.response.RegisterResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;

@Component
public class RegistrationMapper {

    public Family toFamilyEntity(RegisterRequest request, String inviteCode) {
        return Family.builder()
                .familyName(request.getFamilyName())
                .inviteCode(inviteCode)
                .build();
    }

    public Member toMemberEntity(RegisterRequest request, Family family, String encodedPassword) {
        return Member.builder()
                .family(family)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(encodedPassword)
                .role("ADMIN")
                .status("ACTIVE")
                .build();
    }

    public RegisterResponse toRegisterResponse(Family family, Member member) {
        return RegisterResponse.builder()
                .familyId(family.getId())
                .memberId(member.getId())
                .familyName(family.getFamilyName())
                .inviteCode(family.getInviteCode())
                .firstName(member.getFirstName())
                .email(member.getEmail())
                .message("Registration successful")
                .build();
    }
}
