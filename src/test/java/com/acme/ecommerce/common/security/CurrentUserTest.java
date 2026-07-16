package com.acme.ecommerce.common.security;

import com.acme.ecommerce.auth.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit specification for reading the authenticated principal from Spring Security's context.
 *
 * <p>The helper is intentionally small but important because controllers rely on it instead of
 * accepting user ids from request bodies or path parameters.</p>
 */
class CurrentUserTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void require_shouldReturnAuthenticatedUser_whenPrincipalHasExpectedType() {
        // Given
        UUID userId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(userId, "customer@example.com", UserRole.CUSTOMER);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null));

        // When
        AuthenticatedUser result = CurrentUser.require();

        // Then
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("customer@example.com");
        assertThat(result.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void require_shouldThrowIllegalStateException_whenSecurityContextHasNoAuthentication() {
        // Given
        SecurityContextHolder.clearContext();

        // When / Then
        assertThatThrownBy(CurrentUser::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated user is not available");
    }

    @Test
    void require_shouldThrowIllegalStateException_whenPrincipalIsNotAuthenticatedUser() {
        // Given
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("raw-user", null));

        // When / Then
        assertThatThrownBy(CurrentUser::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Authenticated user is not available");
    }
}
