package com.acme.ecommerce.catalog.dto;

import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.common.money.CurrencyCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID sellerId,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        String sku,
        BigDecimal price,
        CurrencyCode currency,
        ProductStatus status,
        int versionNumber,
        List<ProductAttributeResponse> attributes
) {
}
