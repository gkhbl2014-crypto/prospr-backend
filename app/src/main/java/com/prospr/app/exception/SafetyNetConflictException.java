package com.prospr.app.exception;

/** A Safety Net write conflicts with existing state - e.g. a duplicate active allocation, or an
 *  attempt to hand-edit a Setu-synced insurance policy. Maps to 409 Conflict. */
public class SafetyNetConflictException extends RuntimeException {

    public SafetyNetConflictException(String message) {
        super(message);
    }
}
