package com.acme.ecommerce.catalog.dto;

import com.acme.ecommerce.catalog.enums.AttributeType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Attribute metadata returned to Admin/Seller/Customer UX.
 * Customer-facing screens use only attributes where visibleToCustomer is true.
 */
public record AttributeDefinitionResponse(
        UUID id,
        String name,
        String code,
        String labelText,
        AttributeType attributeType,
        boolean required,
        boolean searchable,
        boolean visibleToCustomer,
        Integer minLength,
        Integer maxLength,
        BigDecimal minValue,
        BigDecimal maxValue,
        String allowedValues
) {
}
