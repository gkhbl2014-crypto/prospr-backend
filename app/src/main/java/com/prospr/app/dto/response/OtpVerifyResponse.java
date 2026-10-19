package com.prospr.app.dto.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {@code verificationId} is what registration must present to prove the number was verified. */
@Getter
@AllArgsConstructor
public class OtpVerifyResponse {
    private UUID verificationId;
}
