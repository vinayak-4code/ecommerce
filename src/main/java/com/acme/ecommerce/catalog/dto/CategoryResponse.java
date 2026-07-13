package com.acme.ecommerce.catalog.dto;

import java.util.List;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID parentId,
        String name,
        String slug,
        boolean active,
        List<AttributeDefinitionResponse> attributes
) {
}
