package com.acme.ecommerce.auth.validation;

import com.acme.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit specification for authentication request normalization and password policy. */
class AuthRequestValidatorTest {

    private final AuthRequestValidator validator = new AuthRequestValidator();

    @Test
    void validatePassword_shouldAllowPassword_whenItHasMinimumLengthLetterAndNumber() {
        // Given
        String password = "Password1";

        // When
        validator.validatePassword(password);

        // Then
        // No exception means the password can be used by signup flows.
    }

    @Test
    void validatePassword_shouldRejectPassword_whenItIsTooShortOrMissingLetterOrNumber() {
        // Given / When / Then
        assertThatThrownBy(() -> validator.validatePassword("short1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Password must be at least 8 characters");
        assertThatThrownBy(() -> validator.validatePassword("onlyletters"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("include at least one letter and one number");
        assertThatThrownBy(() -> validator.validatePassword("12345678"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("include at least one letter and one number");
    }

    @Test
    void normalizeEmail_shouldTrimAndLowercaseEmail_whenEmailIsProvided() {
        // Given
        String email = "  Customer@Example.COM  ";

        // When
        String normalized = validator.normalizeEmail(email);

        // Then
        assertThat(normalized).isEqualTo("customer@example.com");
    }

    @Test
    void normalizeEmail_shouldReturnNull_whenEmailIsNull() {
        // Given / When / Then
        assertThat(validator.normalizeEmail(null)).isNull();
    }
}
