package com.acme.ecommerce.inventory.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.acme.ecommerce.inventory.dto.InventoryResponse;
import com.acme.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @PutMapping("/adjustments")
    public InventoryResponse adjust(@Valid @RequestBody InventoryAdjustmentRequest request) {
        return inventoryService.adjust(CurrentUser.require().userId(), request);
    }

    @GetMapping("/products/{productId}/warehouses/{warehouseId}")
    public InventoryResponse get(@PathVariable UUID productId, @PathVariable UUID warehouseId) {
        return inventoryService.getByProductAndWarehouse(productId, warehouseId);
    }
}
