package com.acme.ecommerce.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "auth_tokens", indexes = {
        @Index(name = "idx_auth_token_user", columnList = "user_id"),
        @Index(name = "idx_auth_token_expires", columnList = "expires_at")
})
public class AuthToken {
    @Id
    @Column(length = 80)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isUsableAt(Instant instant) {
        return !revoked && expiresAt.isAfter(instant);
    }
}
