package com.acme.ecommerce.seller.service;

import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.inventory.repository.InventoryItemRepository;
import com.acme.ecommerce.seller.dto.CreateWarehouseRequest;
import com.acme.ecommerce.seller.dto.UpdateWarehouseRequest;
import com.acme.ecommerce.seller.dto.WarehouseResponse;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import com.acme.ecommerce.seller.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seller-owned warehouse management service.
 *
 * <p>Warehouses are deliberately simple in the demo: they identify where stock is
 * stored and who owns it. Location-aware availability can later be added without
 * changing cart/order APIs.</p>
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final SellerService sellerService;
    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;

    /**
     * Creates a warehouse under the authenticated seller.
     */
    @Transactional
    public WarehouseResponse create(UUID userId, CreateWarehouseRequest request) {
        SellerProfile seller = sellerService.requireByUserId(userId);
        Warehouse warehouse = new Warehouse();
        warehouse.setSellerProfile(seller);
        warehouse.setName(request.name().trim());
        warehouse.setCode(request.code().trim().toUpperCase());
        warehouse.setAddressLine1(request.addressLine1().trim());
        warehouse.setCity(request.city().trim());
        warehouse.setState(request.state().trim());
        warehouse.setCountry(request.country().trim());
        warehouse.setPostalCode(request.postalCode().trim());
        return toResponse(warehouseRepository.save(warehouse));
    }

    /**
     * Lists warehouses owned by the authenticated seller with bounded pagination.
     */
    @Transactional(readOnly = true)
    public Page<WarehouseResponse> list(UUID userId, int page, int size) {
        SellerProfile seller = sellerService.requireByUserId(userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "name"));
        return warehouseRepository.findBySellerProfileId(seller.getId(), pageable).map(this::toResponse);
    }

    /**
     * Updates a seller-owned warehouse after ownership validation.
     */
    @Transactional
    public WarehouseResponse update(UUID userId, UUID warehouseId, UpdateWarehouseRequest request) {
        SellerProfile seller = sellerService.requireByUserId(userId);
        Warehouse warehouse = warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
        warehouse.setName(request.name().trim());
        warehouse.setAddressLine1(request.addressLine1().trim());
        warehouse.setCity(request.city().trim());
        warehouse.setState(request.state().trim());
        warehouse.setCountry(request.country().trim());
        warehouse.setPostalCode(request.postalCode().trim());
        warehouse.setStatus(request.status());
        return toResponse(warehouseRepository.save(warehouse));
    }


    /**
     * Deletes a seller-owned warehouse only when no inventory rows are attached.
     */
    @Transactional
    public void delete(UUID userId, UUID warehouseId) {
        SellerProfile seller = sellerService.requireByUserId(userId);
        Warehouse warehouse = warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
        if (inventoryItemRepository.existsByWarehouseId(warehouseId)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Warehouse has inventory. Clear inventory before deleting it.");
        }
        warehouseRepository.delete(warehouse);
    }

    /**
     * Loads a warehouse only when it belongs to the authenticated seller.
     */
    @Transactional(readOnly = true)
    public Warehouse requireSellerWarehouse(UUID userId, UUID warehouseId) {
        SellerProfile seller = sellerService.requireByUserId(userId);
        return warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
    }

    private WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getName(),
                warehouse.getCode(),
                warehouse.getAddressLine1(),
                warehouse.getCity(),
                warehouse.getState(),
                warehouse.getCountry(),
                warehouse.getPostalCode(),
                warehouse.getStatus()
        );
    }
}
