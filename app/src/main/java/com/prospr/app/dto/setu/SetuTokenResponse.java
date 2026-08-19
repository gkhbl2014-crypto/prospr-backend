package com.prospr.app.dto.setu;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SetuTokenResponse {

    @JsonAlias({"access_token", "accessToken", "token"})
    private String accessToken;
}