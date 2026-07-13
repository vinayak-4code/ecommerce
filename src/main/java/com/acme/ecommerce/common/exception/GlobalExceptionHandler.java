package com.acme.ecommerce.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<ApiError.FieldViolation> fields = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldViolation)
                .toList();
        ApiError error = new ApiError(
                java.time.Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                fields
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception) {
        ApiError error = ApiError.of(HttpStatus.BAD_REQUEST.value(), ErrorCode.VALIDATION_FAILED, exception.getMessage());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicate(DuplicateResourceException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case INSUFFICIENT_INVENTORY, COUPON_NOT_APPLICABLE -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> handleForbidden(Exception exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), ErrorCode.ACCESS_DENIED, exception.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(), ErrorCode.AUTHENTICATION_FAILED, "Invalid credentials"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        log.error("Error processing the request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), ErrorCode.BUSINESS_RULE_VIOLATION, "Unexpected server error"));
    }

    private ApiError.FieldViolation toFieldViolation(FieldError fieldError) {
        return new ApiError.FieldViolation(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
