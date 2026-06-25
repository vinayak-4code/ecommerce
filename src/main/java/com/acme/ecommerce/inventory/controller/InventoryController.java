package com.acme.ecommerce.inventory.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.inventory.dto.BulkInventoryUpdateRequest;
import com.acme.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.acme.ecommerce.inventory.dto.InventoryResponse;
import com.acme.ecommerce.inventory.dto.InventoryUpdateRequest;
import com.acme.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Seller inventory API.
 * Supports absolute single/bulk updates and adjustment operations. A quantity of
 * zero is valid and will make search/cart responses show the item as out of stock.
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    /**
     * Sets absolute available quantity for one product and warehouse; zero is accepted.
     */
    @PutMapping
    public InventoryResponse update(@Valid @RequestBody InventoryUpdateRequest request) {
        return inventoryService.update(CurrentUser.require().userId(), request);
    }

    /**
     * Applies up to 100 absolute inventory updates in one seller request.
     */
    @PutMapping("/bulk")
    public List<InventoryResponse> bulkUpdate(@Valid @RequestBody BulkInventoryUpdateRequest request) {
        return inventoryService.bulkUpdate(CurrentUser.require().userId(), request);
    }

    /**
     * Applies a positive or negative inventory delta for controlled stock corrections.
     */
    @PutMapping("/adjustments")
    public InventoryResponse adjust(@Valid @RequestBody InventoryAdjustmentRequest request) {
        return inventoryService.adjust(CurrentUser.require().userId(), request);
    }

    /**
     * Reads inventory for a single product/warehouse pair.
     */
    @GetMapping("/products/{productId}/warehouses/{warehouseId}")
    public InventoryResponse get(@PathVariable UUID productId, @PathVariable UUID warehouseId) {
        return inventoryService.getByProductAndWarehouse(productId, warehouseId);
    }
}
