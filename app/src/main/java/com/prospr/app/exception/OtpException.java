package com.prospr.app.exception;

import org.springframework.http.HttpStatus;

/** Anything that stops an OTP being sent or verified - carries the HTTP status to answer with. */
public class OtpException extends RuntimeException {

    private final HttpStatus status;

    public OtpException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
