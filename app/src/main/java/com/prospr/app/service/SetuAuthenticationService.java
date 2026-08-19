package com.prospr.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.prospr.app.config.SetuProperties;
import com.prospr.app.dto.setu.SetuAuthenticationRequest;
import com.prospr.app.dto.setu.SetuTokenResponse;
import com.prospr.app.exception.SetuIntegrationException;

@Service
public class SetuAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(SetuAuthenticationService.class);

    private final WebClient webClient;
    private final SetuProperties properties;

    public SetuAuthenticationService(WebClient webClient, SetuProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public String obtainBearerToken() {
        SetuAuthenticationRequest request = new SetuAuthenticationRequest(
                properties.getClientId(), "client_credentials", properties.getClientSecret());
        log.info("Calling Setu authentication endpoint with client '{}' and client header '{}'",
                mask(properties.getClientId()), properties.getClient());

        try {
            SetuTokenResponse response = webClient.post()
                    .uri(properties.getAuthUrl())
                    .header("client", properties.getClient())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SetuTokenResponse.class)
                    .block();

            if (response == null || response.getAccessToken() == null || response.getAccessToken().isBlank()) {
                throw new SetuIntegrationException("Setu authentication returned no access token");
            }
            log.info("Setu authentication succeeded; access token received");
            return response.getAccessToken();
        } catch (SetuIntegrationException ex) {
            log.error("Setu authentication failed: {}", ex.getMessage());
            throw ex;
        } catch (WebClientResponseException ex) {
            log.error("Setu authentication failed with status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new SetuIntegrationException("Setu authentication failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Setu authentication request failed: {}", ex.getMessage(), ex);
            throw new SetuIntegrationException("Unable to authenticate with Setu", ex);
        }
    }

    private String mask(String value) {
        if (value == null || value.length() < 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }
}