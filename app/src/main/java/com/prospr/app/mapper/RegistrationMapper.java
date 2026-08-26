package com.prospr.app.mapper;

import org.springframework.stereotype.Component;

import com.prospr.app.dto.request.JoinFamilyRequest;
import com.prospr.app.dto.request.RegisterRequest;
import com.prospr.app.dto.response.JoinFamilyResponse;
import com.prospr.app.dto.response.RegisterResponse;
import com.prospr.app.entity.Family;
import com.prospr.app.entity.Member;

@Component
public class RegistrationMapper {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String STATUS_ACTIVE = "ACTIVE";

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
                .role(ROLE_ADMIN)
                .status(STATUS_ACTIVE)
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

    /** Joining an existing family always creates a non-admin member - the family already has its
     *  admin from whoever registered it. */
    public Member toJoiningMemberEntity(JoinFamilyRequest request, Family family, String encodedPassword) {
        return Member.builder()
                .family(family)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(encodedPassword)
                .role(ROLE_MEMBER)
                .status(STATUS_ACTIVE)
                .build();
    }

    public JoinFamilyResponse toJoinFamilyResponse(Family family, Member member) {
        return JoinFamilyResponse.builder()
                .familyId(family.getId())
                .memberId(member.getId())
                .familyName(family.getFamilyName())
                .firstName(member.getFirstName())
                .email(member.getEmail())
                .message("Joined family successfully")
                .build();
    }
}
