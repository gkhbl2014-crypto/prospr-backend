package com.prospr.app.exception;

/** A Safety Net write fails a business-rule check - e.g. an unrecognized masked account number, or
 *  an allocated amount exceeding the account's known balance. Maps to 400 Bad Request. */
public class SafetyNetValidationException extends RuntimeException {

    public SafetyNetValidationException(String message) {
        super(message);
    }
}
