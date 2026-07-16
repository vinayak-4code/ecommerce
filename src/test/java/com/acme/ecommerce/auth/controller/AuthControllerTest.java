package com.acme.ecommerce.auth.controller;

import com.acme.ecommerce.auth.dto.AuthResponse;
import com.acme.ecommerce.auth.dto.CustomerSignupRequest;
import com.acme.ecommerce.auth.dto.LoginRequest;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for authentication endpoints.
 *
 * <p>These specs focus on service delegation; the controller's job is to
 * pass incoming requests/headers to the AuthService and return the result.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void signupSeller_shouldDelegateRequestToAuthServiceAndReturnResponse() {
        // Given
        SellerSignupRequest request = new SellerSignupRequest("seller@example.com", "Password1", "Acme Seller", "555");
        AuthResponse expected = authResponse(UserRole.SELLER);
        when(authService.signupSeller(request)).thenReturn(expected);

        // When
        AuthResponse response = authController.signupSeller(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(authService).signupSeller(request);
    }

    @Test
    void signupCustomer_shouldDelegateRequestToAuthServiceAndReturnResponse() {
        // Given
        CustomerSignupRequest request = new CustomerSignupRequest("customer@example.com", "Password1", "Jane Customer", "777");
        AuthResponse expected = authResponse(UserRole.CUSTOMER);
        when(authService.signupCustomer(request)).thenReturn(expected);

        // When
        AuthResponse response = authController.signupCustomer(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(authService).signupCustomer(request);
    }

    @Test
    void login_shouldDelegateRequestToAuthServiceAndReturnResponse() {
        // Given
        LoginRequest request = new LoginRequest("admin@example.com", "Password1", UserRole.PRODUCT_ADMIN);
        AuthResponse expected = authResponse(UserRole.PRODUCT_ADMIN);
        when(authService.login(request)).thenReturn(expected);

        // When
        AuthResponse response = authController.login(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(authService).login(request);
    }

    @Test
    void logout_shouldDelegateAuthorizationHeaderToAuthService() {
        // Given
        String authorization = "Bearer token-1";

        // When
        authController.logout(authorization);

        // Then
        verify(authService).logout(authorization);
    }

    private AuthResponse authResponse(UserRole role) {
        return new AuthResponse("Bearer", "access-token", Instant.now().plusSeconds(3600), UUID.randomUUID(), role);
    }
}
