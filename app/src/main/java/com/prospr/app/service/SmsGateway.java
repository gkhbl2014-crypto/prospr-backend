package com.prospr.app.service;

import java.net.URI;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.prospr.app.config.OtpProperties;
import com.prospr.app.exception.OtpException;

/**
 * Delivers OTPs by SMS through MSG91. Fails closed: with no MSG91 credentials configured it refuses
 * to send rather than quietly skipping verification. {@code otp.dev-mode} is the one escape hatch,
 * for local development, and only logs the code.
 */
@Component
public class SmsGateway {

    private static final Logger log = LoggerFactory.getLogger(SmsGateway.class);
    private static final String MSG91_OTP_URL = "https://control.msg91.com/api/v5/otp";

    private final OtpProperties properties;
    private final RestClient restClient = RestClient.create();

    public SmsGateway(OtpProperties properties) {
        this.properties = properties;
    }

    /** @param phone canonical "+91XXXXXXXXXX" form */
    public void sendOtp(String phone, String code) {
        if (properties.isDevMode()) {
            log.warn("[OTP dev mode] OTP for {} is {} - no SMS sent", phone, code);
            return;
        }

        OtpProperties.Msg91 msg91 = properties.getMsg91();
        if (!msg91.isConfigured()) {
            log.error("OTP requested but MSG91 is not configured (set MSG91_AUTH_KEY and MSG91_TEMPLATE_ID)");
            throw new OtpException("SMS delivery is not available right now. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        URI uri = UriComponentsBuilder.fromUriString(MSG91_OTP_URL)
                .queryParam("template_id", msg91.getTemplateId())
                .queryParam("mobile", phone.replace("+", ""))
                .queryParam("otp", code)
                .queryParam("otp_expiry", properties.getExpiryMinutes())
                .build().toUri();

        try {
            Map<String, Object> response = restClient.post()
                    .uri(uri)
                    .header("authkey", msg91.getAuthKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            if (response == null || !"success".equals(response.get("type"))) {
                log.error("MSG91 rejected the OTP request: {}", response);
                throw new OtpException("Couldn't send the OTP. Please try again.", HttpStatus.BAD_GATEWAY);
            }
        } catch (OtpException ex) {
            throw ex;
        } catch (Exception ex) {
            // Log the type only: transport errors embed the request URL, which carries the OTP.
            log.error("MSG91 request failed: {}", ex.getClass().getSimpleName());
            throw new OtpException("Couldn't send the OTP. Please try again.", HttpStatus.BAD_GATEWAY);
        }
    }
}
