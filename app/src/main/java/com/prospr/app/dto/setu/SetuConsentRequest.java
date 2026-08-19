package com.prospr.app.dto.setu;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SetuConsentRequest {

    private Duration consentDuration;
    private String vua;
    private DataRange dataRange;
    private List<String> context;
    private String redirectUrl;

    @Getter
    @Builder
    public static class Duration {
        private String unit;
        private String value;
    }

    @Getter
    @Builder
    public static class DataRange {
        private String from;
        private String to;
    }
}