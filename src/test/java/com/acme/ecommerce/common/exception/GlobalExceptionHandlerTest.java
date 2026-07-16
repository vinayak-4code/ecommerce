package com.acme.ecommerce.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit specification for API error status mapping. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_shouldReturn404WithExceptionErrorCode() {
        // Given
        ResourceNotFoundException exception = new ResourceNotFoundException("Product not found");

        // When
        ResponseEntity<ApiError> response = handler.handleNotFound(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Product not found");
    }

    @Test
    void handleBusiness_shouldReturn409_whenErrorIsInsufficientInventoryOrCouponNotApplicable() {
        // Given
        BusinessException inventoryException = new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Out of stock");
        BusinessException couponException = new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Invalid coupon");

        // When
        ResponseEntity<ApiError> inventoryResponse = handler.handleBusiness(inventoryException);
        ResponseEntity<ApiError> couponResponse = handler.handleBusiness(couponException);

        // Then
        assertThat(inventoryResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(couponResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleBusiness_shouldReturn400_forGenericBusinessRuleViolation() {
        // Given
        BusinessException exception = new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Rule failed");

        // When
        ResponseEntity<ApiError> response = handler.handleBusiness(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    void handleDuplicate_shouldReturn409Conflict() {
        // Given
        DuplicateResourceException exception = new DuplicateResourceException("Duplicate sku");

        // When
        ResponseEntity<ApiError> response = handler.handleDuplicate(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    void handleForbidden_shouldReturn403ForForbiddenOperationAndAccessDenied() {
        // Given / When
        ResponseEntity<ApiError> forbidden = handler.handleForbidden(new ForbiddenOperationException("Forbidden"));
        ResponseEntity<ApiError> accessDenied = handler.handleForbidden(new AccessDeniedException("Denied"));

        // Then
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(accessDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleBadCredentials_shouldReturn401AndHideRawMessage() {
        // Given
        BadCredentialsException exception = new BadCredentialsException("Wrong password");

        // When
        ResponseEntity<ApiError> response = handler.handleBadCredentials(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().message()).isEqualTo("Invalid credentials");
    }

    @Test
    void handleConstraintViolation_shouldReturn400ValidationError() {
        // Given
        ConstraintViolationException exception = new ConstraintViolationException("quantity must be positive", java.util.Set.of());

        // When
        ResponseEntity<ApiError> response = handler.handleConstraintViolation(exception);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
