package com.acme.ecommerce.auth.dto;

import com.acme.ecommerce.auth.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AuthResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        UUID userId,
        UserRole role
) {
}
