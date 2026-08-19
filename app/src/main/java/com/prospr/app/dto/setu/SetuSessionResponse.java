package com.prospr.app.dto.setu;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetuSessionResponse {

    private String id;
    private String consentId;
    private String status;
    private String format;
    private List<Fip> fips;

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fip {
        private List<Account> accounts;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Account {
        private String maskedAccNumber;
        private AccountData data;
    }

    @Getter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountData {
        private String xml;
        private String json;
    }
}
