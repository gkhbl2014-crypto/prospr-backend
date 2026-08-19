package com.prospr.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SetuConsentResponse {

    private String consentUrl;
    private String consentId;
    private String status;
}