package com.prospr.app.exception;

/** A "what if" question that can't be answered as asked (e.g. a loan with no tenure). Maps to 400. */
public class WhatIfValidationException extends RuntimeException {

    public WhatIfValidationException(String message) {
        super(message);
    }
}
