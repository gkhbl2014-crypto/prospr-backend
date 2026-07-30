package com.prospr.app.dto.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private String tokenType;

    private UUID memberId;

    private UUID familyId;

    private String firstName;

    private String lastName;

    private String email;

    private String role;

    private String message;
}
