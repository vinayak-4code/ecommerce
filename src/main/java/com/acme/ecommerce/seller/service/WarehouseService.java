package com.acme.ecommerce.seller.service;

import com.acme.ecommerce.common.exception.ResourceNotFoundException;
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

/** Seller-owned warehouse management service. Warehouse location intelligence is intentionally deferred. */
@Service
@RequiredArgsConstructor
public class WarehouseService {
    private final SellerService sellerService;
    private final WarehouseRepository warehouseRepository;

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

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> list(UUID userId, int page, int size) {
        SellerProfile seller = sellerService.requireByUserId(userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "name"));
        return warehouseRepository.findBySellerProfileId(seller.getId(), pageable).map(this::toResponse);
    }

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
