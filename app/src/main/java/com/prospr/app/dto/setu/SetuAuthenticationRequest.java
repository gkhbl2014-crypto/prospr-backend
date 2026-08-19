package com.prospr.app.dto.setu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SetuAuthenticationRequest {

    @JsonProperty("clientID")
    private String clientId;

    @JsonProperty("grant_type")
    private String grantType;

    private String secret;
}