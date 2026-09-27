package com.prospr.app.exception;

/** Requested goalType isn't one of {@link com.prospr.app.service.GoalType#ALL}. Maps to 400. */
public class GoalValidationException extends RuntimeException {

    public GoalValidationException(String message) {
        super(message);
    }
}
