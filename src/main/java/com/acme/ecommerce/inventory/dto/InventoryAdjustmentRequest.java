package com.acme.ecommerce.inventory.dto;

import com.acme.ecommerce.inventory.enums.InventoryAdjustmentReason;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InventoryAdjustmentRequest(
        @NotNull UUID productId,
        @NotNull UUID warehouseId,
        int quantityChange,
        @NotNull InventoryAdjustmentReason reason
) {
}
