package com.acme.ecommerce.catalog.dto;

import com.acme.ecommerce.catalog.enums.AttributeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Product Admin request for defining one product attribute on a leaf category.
 * Example: RAM can be SELECT with allowed values and visible to customers.
 */
public record AttributeDefinitionRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 80) String code,
        @Size(max = 160) String labelText,
        @NotNull AttributeType attributeType,
        boolean required,
        boolean searchable,
        boolean visibleToCustomer,
        Integer minLength,
        Integer maxLength,
        BigDecimal minValue,
        BigDecimal maxValue,
        @Size(max = 1000) String allowedValues
) {
}
