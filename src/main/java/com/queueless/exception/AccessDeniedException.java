package com.queueless.exception;

/** Thrown when a user tries to perform an action they are not authorized for. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}