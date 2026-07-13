package com.acme.ecommerce.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Product-admin request for updating a category or sub-classification.
 * Passing attributes upserts predefined attributes; old attributes are retained to avoid breaking existing products.
 */
public record UpdateCategoryRequest(
        UUID parentId,
        @NotBlank @Size(max = 140) String name,
        boolean active,
        @Valid List<AttributeDefinitionRequest> attributes
) {
}
