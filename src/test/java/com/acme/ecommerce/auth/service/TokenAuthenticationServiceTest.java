package com.acme.ecommerce.auth.service;

import com.acme.ecommerce.auth.entity.AuthToken;
import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.enums.AccountStatus;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.repository.AuthTokenRepository;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for signed token issuance, authentication, and revocation.
 *
 * <p>The JWT is validated cryptographically by the service and then checked
 * against the persisted AuthToken row so logout can revoke it immediately.</p>
 */
@ExtendWith(MockitoExtension.class)
class TokenAuthenticationServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    private TokenAuthenticationService tokenAuthenticationService;

    @BeforeEach
    void setUp() {
        tokenAuthenticationService = new TokenAuthenticationService(authTokenRepository, new ObjectMapper());
        ReflectionTestUtils.setField(tokenAuthenticationService, "tokenExpiryHours", 12L);
        ReflectionTestUtils.setField(tokenAuthenticationService, "jwtSecret", "unit-test-secret-long-enough-for-hmac");
    }

    @Test
    void issueToken_shouldCreateSignedJwtAndPersistToken() {
        // Given
        UserAccount user = user(UserRole.CUSTOMER);
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AuthToken token = tokenAuthenticationService.issueToken(user);

        // Then
        assertThat(token.getToken()).contains(".");
        assertThat(token.getToken().split("\\.")).hasSize(3);
        assertThat(token.getUserAccount()).isSameAs(user);
        assertThat(token.getExpiresAt()).isAfter(Instant.now());
        verify(authTokenRepository).save(token);
    }

    @Test
    void authenticate_shouldReturnAuthenticatedUser_whenJwtIsValidPersistedAndUsable() {
        // Given
        UserAccount user = user(UserRole.SELLER);
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthToken token = tokenAuthenticationService.issueToken(user);
        when(authTokenRepository.findById(token.getToken())).thenReturn(Optional.of(token));

        // When
        Optional<AuthenticatedUser> authenticatedUser = tokenAuthenticationService.authenticate(token.getToken());

        // Then
        assertThat(authenticatedUser).isPresent();
        assertThat(authenticatedUser.get().userId()).isEqualTo(user.getId());
        assertThat(authenticatedUser.get().email()).isEqualTo(user.getEmail());
        assertThat(authenticatedUser.get().role()).isEqualTo(UserRole.SELLER);
    }

    @Test
    void authenticate_shouldReturnEmpty_whenTokenIsBlankMalformedOrUnsigned() {
        // Given
        String malformedToken = "not.a.valid.token";

        // When / Then
        assertThat(tokenAuthenticationService.authenticate(null)).isEmpty();
        assertThat(tokenAuthenticationService.authenticate(" ")).isEmpty();
        assertThat(tokenAuthenticationService.authenticate(malformedToken)).isEmpty();
    }

    @Test
    void authenticate_shouldReturnEmpty_whenPersistedTokenIsRevoked() {
        // Given
        UserAccount user = user(UserRole.CUSTOMER);
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AuthToken token = tokenAuthenticationService.issueToken(user);
        token.setRevoked(true);
        when(authTokenRepository.findById(token.getToken())).thenReturn(Optional.of(token));

        // When
        Optional<AuthenticatedUser> authenticatedUser = tokenAuthenticationService.authenticate(token.getToken());

        // Then
        assertThat(authenticatedUser).isEmpty();
    }

    @Test
    void revoke_shouldMarkPersistedTokenRevoked_whenBearerHeaderContainsKnownToken() {
        // Given
        AuthToken token = new AuthToken();
        token.setToken("persisted-token");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setUserAccount(user(UserRole.CUSTOMER));
        when(authTokenRepository.findById("persisted-token")).thenReturn(Optional.of(token));

        // When
        tokenAuthenticationService.revoke("Bearer persisted-token");

        // Then
        assertThat(token.isRevoked()).isTrue();
        verify(authTokenRepository).save(token);
    }

    @Test
    void extractBearerToken_shouldReturnNull_whenHeaderIsMissingOrNotBearer() {
        // Given / When / Then
        assertThat(tokenAuthenticationService.extractBearerToken(null)).isNull();
        assertThat(tokenAuthenticationService.extractBearerToken("Basic abc")).isNull();
        assertThat(tokenAuthenticationService.extractBearerToken("Bearer abc")).isEqualTo("abc");
    }

    private UserAccount user(UserRole role) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(role);
        user.setStatus(AccountStatus.ACTIVE);
        return user;
    }
}
