package com.acme.ecommerce.catalog.dto;

import com.acme.ecommerce.catalog.enums.AttributeType;

import java.util.UUID;

public record AttributeDefinitionResponse(
        UUID id,
        String name,
        String code,
        AttributeType attributeType,
        boolean required,
        boolean searchable
) {
}
