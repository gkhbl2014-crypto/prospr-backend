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
public class JoinFamilyResponse {

    private UUID familyId;

    private UUID memberId;

    private String familyName;

    private String firstName;

    private String email;

    private String message;
}
