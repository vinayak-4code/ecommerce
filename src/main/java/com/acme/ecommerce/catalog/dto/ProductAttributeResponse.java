package com.acme.ecommerce.catalog.dto;

public record ProductAttributeResponse(
        String code,
        String name,
        String value
) {
}
