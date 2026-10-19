package com.prospr.app.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
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
public class LoginRequest {

    /** Mobile number, or email for accounts that have one. "email" is accepted as an alias so
     *  clients written before email became optional keep working. */
    @JsonAlias("email")
    @NotBlank(message = "Mobile number cannot be blank")
    private String identifier;

    @NotBlank(message = "Password cannot be blank")
    private String password;
}
