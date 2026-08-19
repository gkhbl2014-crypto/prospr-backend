package com.prospr.app.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SetuSessionResponse {

    private String id;
    private String consentId;
    private String status;
}
