package com.acme.ecommerce.common.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        ErrorCode code,
        String message,
        List<FieldViolation> fieldViolations
) {
    public static ApiError of(int status, ErrorCode code, String message) {
        return new ApiError(Instant.now(), status, code, message, List.of());
    }

    public record FieldViolation(String field, String message) {
    }
}
