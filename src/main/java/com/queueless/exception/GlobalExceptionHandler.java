package com.queueless.exception;

import com.queueless.dto.Dtos.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Global exception handler providing structured JSON error responses for all API errors.
 * Catches validation errors, business logic errors, auth errors, and generic 500s.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** Handles request body validation failures (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationErrors(MethodArgumentNotValidException ex,
                                                 HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return ErrorResponse.builder()
                .status(400)
                .error("Validation Failed")
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }

    /** Handles known business rule violations (custom exception). */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business rule violation at {}: {}", request.getRequestURI(), ex.getMessage());
        return ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }

    /** Handles not-found errors. */
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ErrorResponse.builder()
                .status(404)
                .error("Not Found")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }

    /** Handles Spring Security access denied (403) errors. */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ErrorResponse.builder()
                .status(403)
                .error("Forbidden")
                .message("You do not have permission to perform this action")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }

    /** Handles our custom AccessDeniedException (403). */
    @ExceptionHandler(com.queueless.exception.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleCustomAccessDenied(com.queueless.exception.AccessDeniedException ex,
                                                   HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        return ErrorResponse.builder()
                .status(403)
                .error("Forbidden")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }

    /** Handles database constraint violations (unique keys, check constraints). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDataIntegrity(DataIntegrityViolationException ex,
                                              HttpServletRequest request) {
        log.warn("Data integrity violation at {}: {}", request.getRequestURI(), ex.getMessage());
        String message = "A conflict occurred with existing data.";
        String cause = ex.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains("phone")) {
            message = "A user with this phone number already exists.";
        } else if (cause != null && cause.contains("role_check")) {
            message = "Invalid role. Please restart the server and try again.";
        } else if (cause != null && cause.contains("unique") || (cause != null && cause.contains("duplicate"))) {
            message = "This record already exists.";
        }
        return ErrorResponse.builder()
                .status(409)
                .error("Conflict")
                .message(message)
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }

    /** Handles image/upload validation errors (bad MIME type, oversized file). */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }

    /** Catch-all for unexpected server errors. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ErrorResponse.builder()
                .status(500)
                .error("Internal Server Error")
                .message("An unexpected error occurred. Please try again.")
                .path(request.getRequestURI())
                .timestamp(Instant.now())
                .build();
    }
}