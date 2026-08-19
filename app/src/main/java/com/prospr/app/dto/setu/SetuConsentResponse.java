package com.prospr.app.dto.setu;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SetuConsentResponse {

    @JsonAlias({"id", "consentId"})
    private String consentId;

    @JsonAlias({"url", "consentUrl"})
    private String consentUrl;

    private String status;
}