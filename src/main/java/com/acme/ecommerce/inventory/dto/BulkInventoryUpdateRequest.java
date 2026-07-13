package com.acme.ecommerce.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Bulk inventory update wrapper for seller stock imports. */
public record BulkInventoryUpdateRequest(
        @NotEmpty @Size(max = 100) List<@Valid InventoryUpdateRequest> items
) {
}
