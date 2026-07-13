package com.acme.ecommerce.catalog.dto;

import com.acme.ecommerce.common.money.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 220) String name,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 80) String sku,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @NotNull CurrencyCode currency,
        @Valid List<AttributeValueRequest> attributes
) {
}
