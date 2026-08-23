package com.prospr.app.dto.response;

import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberProfileResponse {

    private UUID id;
    private UUID familyId;
    private String familyName;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
}
