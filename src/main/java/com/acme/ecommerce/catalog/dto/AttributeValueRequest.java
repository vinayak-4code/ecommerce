package com.acme.ecommerce.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttributeValueRequest(
        @NotBlank @Size(max = 80) String code,
        @NotBlank @Size(max = 400) String value
) {
}
