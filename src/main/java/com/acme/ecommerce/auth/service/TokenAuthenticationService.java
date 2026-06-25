package com.acme.ecommerce.auth.service;

import com.acme.ecommerce.auth.entity.AuthToken;
import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.repository.AuthTokenRepository;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues, validates, and revokes opaque bearer tokens.
 *
 * <p>Example token usage: {@code Authorization: Bearer eyJ...}. Tokens are stored
 * server-side so logout can immediately revoke an active session without JWT
 * blacklist infrastructure.</p>
 */
/**
 * Token persistence and lookup service for the simple Bearer-auth model.
 *
 * <p>Tokens are stored server-side, which keeps logout/revocation simple for the
 * demo while avoiding JWT key management. Example: controllers receive
 * Authorization: Bearer token, the filter delegates here, and an AuthenticatedUser
 * is built when the token is active.</p>
 */
@Service
@RequiredArgsConstructor
public class TokenAuthenticationService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenRepository authTokenRepository;

    @Value("${app.security.token-expiry-hours:12}")
    private long tokenExpiryHours;

    /**
     * Creates a new random access token with a fixed expiry window.
     */
    @Transactional
    public AuthToken issueToken(UserAccount userAccount) {
        AuthToken authToken = new AuthToken();
        authToken.setToken(generateToken());
        authToken.setUserAccount(userAccount);
        authToken.setExpiresAt(Instant.now().plus(tokenExpiryHours, ChronoUnit.HOURS));
        return authTokenRepository.save(authToken);
    }

    /**
     * Validates the token, expiry, revocation flag, and account status.
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return authTokenRepository.findById(token)
                .filter(authToken -> authToken.isUsableAt(Instant.now()))
                .map(AuthToken::getUserAccount)
                .map(user -> new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole()));
    }

    /**
     * Marks a Bearer token as revoked; missing headers are treated as no-op.
     */
    @Transactional
    public void revoke(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            return;
        }
        authTokenRepository.findById(token).ifPresent(authToken -> {
            authToken.setRevoked(true);
            authTokenRepository.save(authToken);
        });
    }

    /**
     * Extracts the token value from an Authorization header.
     */
    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }

    private String generateToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
