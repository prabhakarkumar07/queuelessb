package com.queueless.exception;

/** Thrown when a business rule is violated (e.g., queue full, token already exists). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}