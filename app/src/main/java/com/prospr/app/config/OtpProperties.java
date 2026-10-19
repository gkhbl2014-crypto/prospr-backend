package com.prospr.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/** Mobile-number OTP tunables. See application.yml ("otp.*") for the env vars behind each. */
@Configuration
@ConfigurationProperties(prefix = "otp")
@Getter
@Setter
public class OtpProperties {

    private int expiryMinutes = 10;
    private int maxVerifyAttempts = 5;
    private int resendCooldownSeconds = 30;
    private int maxSendsPerHour = 5;
    /** How long a successful verification stays usable for completing registration. */
    private int verifiedValidMinutes = 15;

    /** Logs the OTP instead of sending an SMS. Local development only - never enable in production. */
    private boolean devMode = false;

    private Msg91 msg91 = new Msg91();

    @Getter
    @Setter
    public static class Msg91 {
        private String authKey = "";
        private String templateId = "";

        public boolean isConfigured() {
            return authKey != null && !authKey.isBlank() && templateId != null && !templateId.isBlank();
        }
    }
}
