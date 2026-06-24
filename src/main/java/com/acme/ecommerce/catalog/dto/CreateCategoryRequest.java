package com.acme.ecommerce.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateCategoryRequest(
        UUID parentId,
        @NotBlank @Size(max = 140) String name,
        @Valid List<AttributeDefinitionRequest> attributes
) {
}
