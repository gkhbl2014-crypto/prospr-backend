package com.prospr.app.exception;

/** An uploaded statement file can't be read or has no supported/recognized format. Maps to 400. */
public class ImportValidationException extends RuntimeException {

    public ImportValidationException(String message) {
        super(message);
    }

    public ImportValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
