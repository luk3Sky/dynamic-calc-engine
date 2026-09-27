package com.payroll.api.web.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Structured error body returned to clients. Never includes a stack trace.
 */
@Getter
@AllArgsConstructor
public class ApiError {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String message;

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message);
    }
}