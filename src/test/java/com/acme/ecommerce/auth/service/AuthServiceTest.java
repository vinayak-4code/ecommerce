package com.acme.ecommerce.auth.service;

import com.acme.ecommerce.auth.dto.AuthResponse;
import com.acme.ecommerce.auth.dto.CustomerSignupRequest;
import com.acme.ecommerce.auth.dto.LoginRequest;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.entity.AuthToken;
import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.enums.AccountStatus;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.repository.UserAccountRepository;
import com.acme.ecommerce.auth.validation.AuthRequestValidator;
import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ForbiddenOperationException;
import com.acme.ecommerce.customer.repository.CustomerProfileRepository;
import com.acme.ecommerce.seller.repository.SellerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit specification for account creation, login, and logout orchestration.
 *
 * <p>The service coordinates validators, repositories, password encoding, and
 * token issuance without starting the Spring container.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAuthenticationService tokenAuthenticationService;

    @Mock
    private AuthRequestValidator authRequestValidator;

    @InjectMocks
    private AuthService authService;

    @Test
    void signupSeller_shouldCreateSellerAccountProfileAndIssueBearerToken_whenEmailIsAvailable() {
        // Given
        SellerSignupRequest request = new SellerSignupRequest(" Seller@Example.COM ", "Password1", " Acme Seller ", "9999999999");
        UserAccount savedUser = user(UUID.randomUUID(), "seller@example.com", UserRole.SELLER, AccountStatus.ACTIVE);
        AuthToken token = token(savedUser, "jwt-seller");

        when(authRequestValidator.normalizeEmail(request.email())).thenReturn("seller@example.com");
        when(userAccountRepository.existsByEmailIgnoreCase("seller@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(savedUser);
        when(tokenAuthenticationService.issueToken(savedUser)).thenReturn(token);

        // When
        AuthResponse response = authService.signupSeller(request);

        // Then
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isEqualTo("jwt-seller");
        assertThat(response.userId()).isEqualTo(savedUser.getId());
        assertThat(response.role()).isEqualTo(UserRole.SELLER);

        verify(authRequestValidator).validatePassword(request.password());
        verify(sellerProfileRepository).save(any());
        verify(tokenAuthenticationService).issueToken(savedUser);
    }

    @Test
    void signupCustomer_shouldCreateCustomerAccountProfileAndIssueBearerToken_whenEmailIsAvailable() {
        // Given
        CustomerSignupRequest request = new CustomerSignupRequest(" Customer@Example.COM ", "Password1", " Demo Customer ", "9999999999");
        UserAccount savedUser = user(UUID.randomUUID(), "customer@example.com", UserRole.CUSTOMER, AccountStatus.ACTIVE);
        AuthToken token = token(savedUser, "jwt-customer");

        when(authRequestValidator.normalizeEmail(request.email())).thenReturn("customer@example.com");
        when(userAccountRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userAccountRepository.save(any(UserAccount.class))).thenReturn(savedUser);
        when(tokenAuthenticationService.issueToken(savedUser)).thenReturn(token);

        // When
        AuthResponse response = authService.signupCustomer(request);

        // Then
        assertThat(response.accessToken()).isEqualTo("jwt-customer");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        verify(authRequestValidator).validatePassword(request.password());
        verify(customerProfileRepository).save(any());
    }

    @Test
    void signupSeller_shouldThrowDuplicateResourceException_whenEmailAlreadyExists() {
        // Given
        SellerSignupRequest request = new SellerSignupRequest("seller@example.com", "Password1", "Acme Seller", null);
        when(authRequestValidator.normalizeEmail(request.email())).thenReturn("seller@example.com");
        when(userAccountRepository.existsByEmailIgnoreCase("seller@example.com")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> authService.signupSeller(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email is already registered");

        verifyNoInteractions(passwordEncoder, tokenAuthenticationService, sellerProfileRepository);
    }

    @Test
    void login_shouldIssueToken_whenCredentialsRoleAndStatusAreValid() {
        // Given
        LoginRequest request = new LoginRequest("Customer@Example.COM", "Password1", UserRole.CUSTOMER);
        UserAccount user = user(UUID.randomUUID(), "customer@example.com", UserRole.CUSTOMER, AccountStatus.ACTIVE);
        AuthToken token = token(user, "jwt-login");

        when(authRequestValidator.normalizeEmail(request.email())).thenReturn("customer@example.com");
        when(userAccountRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(true);
        when(tokenAuthenticationService.issueToken(user)).thenReturn(token);

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response.accessToken()).isEqualTo("jwt-login");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        verify(tokenAuthenticationService).issueToken(user);
    }

    @Test
    void login_shouldRejectCrossRoleLoginAttempt_whenRequestedRoleDoesNotMatchAccountRole() {
        // Given
        LoginRequest request = new LoginRequest("seller@example.com", "Password1", UserRole.CUSTOMER);
        UserAccount user = user(UUID.randomUUID(), "seller@example.com", UserRole.SELLER, AccountStatus.ACTIVE);
        when(authRequestValidator.normalizeEmail(request.email())).thenReturn("seller@example.com");
        when(userAccountRepository.findByEmailIgnoreCase("seller@example.com")).thenReturn(Optional.of(user));

        // When / Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");

        verifyNoInteractions(tokenAuthenticationService);
    }

    @Test
    void login_shouldRejectInactiveAccount_whenAccountStatusIsNotActive() {
        // Given
        LoginRequest request = new LoginRequest("customer@example.com", "Password1", UserRole.CUSTOMER);
        UserAccount user = user(UUID.randomUUID(), "customer@example.com", UserRole.CUSTOMER, AccountStatus.DISABLED);
        when(authRequestValidator.normalizeEmail(request.email())).thenReturn("customer@example.com");
        when(userAccountRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));

        // When / Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Account is not active");
    }

    @Test
    void login_shouldRejectInvalidPassword_whenPasswordDoesNotMatchHash() {
        // Given
        LoginRequest request = new LoginRequest("customer@example.com", "wrong", UserRole.CUSTOMER);
        UserAccount user = user(UUID.randomUUID(), "customer@example.com", UserRole.CUSTOMER, AccountStatus.ACTIVE);
        when(authRequestValidator.normalizeEmail(request.email())).thenReturn("customer@example.com");
        when(userAccountRepository.findByEmailIgnoreCase("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPasswordHash())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void logout_shouldDelegateBearerHeaderToTokenAuthenticationService() {
        // Given
        String authorizationHeader = "Bearer token";

        // When
        authService.logout(authorizationHeader);

        // Then
        verify(tokenAuthenticationService).revoke(authorizationHeader);
    }

    private UserAccount user(UUID userId, String email, UserRole role, AccountStatus status) {
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail(email);
        user.setPasswordHash("hashed-password");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private AuthToken token(UserAccount user, String value) {
        AuthToken token = new AuthToken();
        token.setToken(value);
        token.setUserAccount(user);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
