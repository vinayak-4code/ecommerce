package com.acme.ecommerce.auth.dto;

import com.acme.ecommerce.auth.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        @NotNull UserRole role
) {
}
