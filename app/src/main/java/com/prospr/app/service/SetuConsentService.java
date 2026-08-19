package com.prospr.app.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.prospr.app.config.SetuProperties;
import com.prospr.app.dto.response.SetuConsentResponse;
import com.prospr.app.dto.setu.SetuConsentRequest;
import com.prospr.app.exception.SetuIntegrationException;

@Service
public class SetuConsentService {

    private static final Logger log = LoggerFactory.getLogger(SetuConsentService.class);

    private final WebClient webClient;
    private final SetuProperties properties;
    private final SetuAuthenticationService authenticationService;

    public SetuConsentService(WebClient webClient, SetuProperties properties,
                              SetuAuthenticationService authenticationService) {
        this.webClient = webClient;
        this.properties = properties;
        this.authenticationService = authenticationService;
    }

    public SetuConsentResponse createConsent(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            throw new SetuIntegrationException("Logged-in user does not have a registered mobile number");
        }
        SetuConsentRequest request = SetuConsentRequest.builder()
                .consentDuration(SetuConsentRequest.Duration.builder()
                        .unit(properties.getConsentDuration().getUnit())
                        .value(properties.getConsentDuration().getValue())
                        .build())
                .vua(mobile + "@dashboard-aa-preprod")
                .dataRange(SetuConsentRequest.DataRange.builder()
                        .from(properties.getDataRange().getFrom())
                        .to(properties.getDataRange().getTo())
                        .build())
                .context(List.of())
                .redirectUrl(properties.getRedirectUrl())
                .build();

        log.info("Creating Setu consent for VUA '{}'", maskVua(request.getVua()));
        try {
            com.prospr.app.dto.setu.SetuConsentResponse response = webClient.post()
                    .uri(properties.getConsentUrl())
                    .headers(headers -> {
                        headers.setBearerAuth(authenticationService.obtainBearerToken());
                        headers.set("x-product-instance-id", properties.getProductInstanceId());
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(com.prospr.app.dto.setu.SetuConsentResponse.class)
                    .block();

            if (response == null || response.getConsentUrl() == null || response.getConsentUrl().isBlank()) {
                throw new SetuIntegrationException("Setu consent response did not contain a consent URL");
            }
            log.info("Setu consent created with id '{}' and status '{}'", response.getConsentId(), response.getStatus());
            return SetuConsentResponse.builder()
                    .consentUrl(response.getConsentUrl())
                    .consentId(response.getConsentId())
                    .status(response.getStatus() == null ? "CREATED" : response.getStatus())
                    .build();
        } catch (SetuIntegrationException ex) {
            log.error("Setu consent creation failed: {}", ex.getMessage());
            throw ex;
        } catch (WebClientResponseException ex) {
            log.error("Setu consent failed with status {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new SetuIntegrationException("Setu consent creation failed: " + ex.getResponseBodyAsString(), ex);
        } catch (Exception ex) {
            log.error("Setu consent request failed: {}", ex.getMessage(), ex);
            throw new SetuIntegrationException("Unable to create Setu consent", ex);
        }
    }

    private String maskVua(String vua) {
        int separator = vua.indexOf('@');
        if (separator <= 2) {
            return "***@dashboard-aa-preprod";
        }
        return vua.substring(0, 2) + "******" + vua.substring(separator);
    }
}