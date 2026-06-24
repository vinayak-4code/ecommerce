package com.acme.ecommerce.auth.repository;

import com.acme.ecommerce.auth.entity.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
}
