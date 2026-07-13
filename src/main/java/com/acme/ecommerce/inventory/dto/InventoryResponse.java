package com.acme.ecommerce.inventory.dto;

import java.util.UUID;

public record InventoryResponse(
        UUID productId,
        UUID warehouseId,
        int availableQuantity,
        int reservedQuantity,
        long consolidatedAvailableQuantity
) {
}
