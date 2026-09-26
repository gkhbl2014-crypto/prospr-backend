package com.prospr.app.exception;

/** Requested tag value isn't one of INVESTMENT|ESSENTIAL|LIFESTYLE_CREEP|AUTO. Maps to 400. */
public class TransactionTagException extends RuntimeException {

    public TransactionTagException(String message) {
        super(message);
    }
}
