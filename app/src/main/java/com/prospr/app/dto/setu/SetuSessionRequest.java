package com.prospr.app.dto.setu;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SetuSessionRequest {

    private DataRange dataRange;
    private String consentId;
    private String format;

    @Getter
    @Builder
    public static class DataRange {
        private String from;
        private String to;
    }
}
