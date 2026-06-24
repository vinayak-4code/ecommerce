package com.acme.ecommerce.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarehouseRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 240) String addressLine1,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 120) String state,
        @NotBlank @Size(max = 120) String country,
        @NotBlank @Size(max = 20) String postalCode
) {
}
