package com.acme.ecommerce.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerSignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 80) String password,
        @NotBlank @Size(max = 180) String businessName,
        @Size(max = 40) String contactNumber
) {
}
