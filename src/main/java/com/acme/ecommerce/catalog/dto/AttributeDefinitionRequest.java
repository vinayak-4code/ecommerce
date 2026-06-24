package com.acme.ecommerce.catalog.dto;

import com.acme.ecommerce.catalog.enums.AttributeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AttributeDefinitionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String code,
        @NotNull AttributeType attributeType,
        boolean required,
        boolean searchable
) {
}
