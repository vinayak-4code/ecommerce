package com.acme.ecommerce.inventory.dto;

import com.acme.ecommerce.inventory.enums.InventoryAdjustmentReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Seller request for setting absolute warehouse inventory.
 * availableQuantity may be zero, which makes the product out of stock.
 */
public record InventoryUpdateRequest(
        @NotNull UUID productId,
        @NotNull UUID warehouseId,
        @Min(0) int availableQuantity,
        @NotNull InventoryAdjustmentReason reason
) {
}
