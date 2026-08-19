package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "setu")
@Getter
@Setter
public class SetuProperties {

    private String authUrl;
    private String consentUrl;
    private String sessionUrl;
    private String redirectUrl;
    private String clientId;
    private String clientSecret;
    private String productInstanceId;
    private String client;
    private String format = "xml";
    private ConsentDuration consentDuration = new ConsentDuration();
    private DataRange dataRange = new DataRange();

    @Getter
    @Setter
    public static class ConsentDuration {
        private String unit = "MONTH";
        private String value = "24";
    }

    @Getter
    @Setter
    public static class DataRange {
        private String from;
        private String to;
    }

}
