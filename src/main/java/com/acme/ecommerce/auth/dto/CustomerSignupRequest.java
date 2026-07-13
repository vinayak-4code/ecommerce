package com.acme.ecommerce.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerSignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 80) String password,
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 40) String phoneNumber
) {
}
