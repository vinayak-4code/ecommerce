package com.acme.ecommerce.inventory.service;

import java.util.UUID;

public record InventoryReservationAllocation(
        UUID reservationId,
        UUID productId,
        UUID warehouseId,
        int quantity
) {
}
