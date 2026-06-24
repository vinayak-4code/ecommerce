package com.acme.ecommerce.search.dto;

import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.common.money.CurrencyCode;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record ProductSearchResponse(
        UUID productId,
        UUID sellerId,
        UUID categoryId,
        String categoryName,
        String name,
        String description,
        String sku,
        BigDecimal price,
        CurrencyCode currency,
        ProductStatus status,
        Map<String, String> attributes,
        long totalAvailableQuantity
) {
}
