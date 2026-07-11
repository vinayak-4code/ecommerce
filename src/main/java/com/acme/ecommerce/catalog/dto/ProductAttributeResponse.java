package com.acme.ecommerce.catalog.dto;

/** Product attribute value with enough metadata for seller editing and customer specification display. */
public record ProductAttributeResponse(
        String code,
        String name,
        String labelText,
        String value,
        boolean visibleToCustomer
) {
}
