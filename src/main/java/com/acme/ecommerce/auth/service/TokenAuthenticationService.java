package com.acme.ecommerce.auth.service;

import com.acme.ecommerce.auth.entity.AuthToken;
import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.repository.AuthTokenRepository;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, validates, and revokes signed JWT Bearer tokens.
 *
 * <p>The token is a compact HMAC-SHA256 JWT containing subject, email, role, and
 * expiry. It is also persisted server-side so logout can revoke it immediately
 * without introducing gateway/session infrastructure.</p>
 */
@Service
@RequiredArgsConstructor
public class TokenAuthenticationService {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthTokenRepository authTokenRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.security.token-expiry-hours:12}")
    private long tokenExpiryHours;

    @Value("${app.security.jwt-secret:change-me-demo-secret-change-in-production}")
    private String jwtSecret;

    /** Creates and persists a signed JWT access token. */
    @Transactional
    public AuthToken issueToken(UserAccount userAccount) {
        Instant expiresAt = Instant.now().plus(tokenExpiryHours, ChronoUnit.HOURS);
        AuthToken authToken = new AuthToken();
        authToken.setToken(generateJwt(userAccount, expiresAt));
        authToken.setUserAccount(userAccount);
        authToken.setExpiresAt(expiresAt);
        return authTokenRepository.save(authToken);
    }

    /** Validates token signature, revocation state, expiry, and active account. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String token) {
        if (token == null || token.isBlank() || !isValidJwt(token)) {
            return Optional.empty();
        }
        return authTokenRepository.findById(token)
                .filter(authToken -> authToken.isUsableAt(Instant.now()))
                .map(AuthToken::getUserAccount)
                .map(user -> new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole()));
    }

    /** Revokes the exact Bearer token presented by the caller. */
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

    /** Extracts the token value from an Authorization header. */
    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length()).trim();
    }

    private String generateJwt(UserAccount userAccount, Instant expiresAt) {
        try {
            String header = base64Url(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = base64Url(objectMapper.writeValueAsBytes(Map.of(
                    "sub", userAccount.getId().toString(),
                    "email", userAccount.getEmail(),
                    "role", userAccount.getRole().name(),
                    "iat", Instant.now().getEpochSecond(),
                    "exp", expiresAt.getEpochSecond()
            )));
            String unsigned = header + "." + payload;
            return unsigned + "." + sign(unsigned);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to issue JWT", exception);
        }
    }

    private boolean isValidJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            String unsigned = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(unsigned).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return false;
            }
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            Object exp = payload.get("exp");
            if (exp == null) {
                return false;
            }
            long epochSeconds = Long.parseLong(String.valueOf(exp));
            return Instant.ofEpochSecond(epochSeconds).isAfter(Instant.now());
        } catch (Exception exception) {
            return false;
        }
    }

    private String sign(String unsigned) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return base64Url(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
