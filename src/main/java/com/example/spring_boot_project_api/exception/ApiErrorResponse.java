package com.example.spring_boot_project_api.exception;

import java.time.Instant;

/**
 * Uniform error body returned by {@link GlobalExceptionHandler} for every failure.
 * Never includes internal details, secrets or OTP codes.
 */
public record ApiErrorResponse(int status, String error, String message, Instant timestamp) {
}
