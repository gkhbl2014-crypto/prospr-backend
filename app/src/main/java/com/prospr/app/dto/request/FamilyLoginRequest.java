package com.prospr.app.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class FamilyLoginRequest {

    @NotBlank(message = "Family code cannot be blank")
    private String inviteCode;

    @NotNull(message = "Member is required")
    private UUID memberId;

    @NotBlank(message = "Password cannot be blank")
    private String password;
}
