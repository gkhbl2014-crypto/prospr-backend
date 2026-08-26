package com.prospr.app.exception;

public class LifestyleAnalysisException extends RuntimeException {

    public LifestyleAnalysisException(String message) {
        super(message);
    }

    public LifestyleAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
