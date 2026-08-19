package com.prospr.app.exception;

public class SetuIntegrationException extends RuntimeException {

    public SetuIntegrationException(String message) {
        super(message);
    }

    public SetuIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}