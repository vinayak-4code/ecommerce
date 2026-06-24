package com.acme.ecommerce.inventory.service;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ForbiddenOperationException;
import com.acme.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.acme.ecommerce.inventory.dto.InventoryResponse;
import com.acme.ecommerce.inventory.entity.InventoryItem;
import com.acme.ecommerce.inventory.entity.InventoryReservation;
import com.acme.ecommerce.inventory.enums.InventoryReservationStatus;
import com.acme.ecommerce.inventory.repository.InventoryItemRepository;
import com.acme.ecommerce.inventory.repository.InventoryReservationRepository;
import com.acme.ecommerce.seller.entity.Warehouse;
import com.acme.ecommerce.seller.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final String AGGREGATE_TYPE = "Inventory";

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryReservationRepository reservationRepository;
    private final WarehouseService warehouseService;
    private final ProductService productService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public InventoryResponse adjust(UUID sellerUserId, InventoryAdjustmentRequest request) {
        if (request.quantityChange() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Inventory quantity change cannot be zero");
        }
        Warehouse warehouse = warehouseService.requireSellerWarehouse(sellerUserId, request.warehouseId());
        Product product = productService.requireProduct(request.productId());
        if (!product.getSellerProfile().getId().equals(warehouse.getSellerProfile().getId())) {
            throw new ForbiddenOperationException("Seller cannot adjust inventory for another seller product");
        }
        InventoryItem inventoryItem = inventoryItemRepository
                .lockByProductIdAndWarehouseId(request.productId(), request.warehouseId())
                .orElseGet(() -> createEmptyInventoryItem(product.getId(), warehouse));

        int newAvailable = inventoryItem.getAvailableQuantity() + request.quantityChange();
        if (newAvailable < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Inventory adjustment would make available quantity negative");
        }
        inventoryItem.setAvailableQuantity(newAvailable);
        InventoryItem saved = inventoryItemRepository.save(inventoryItem);
        DomainEventType eventType = request.quantityChange() > 0 ? DomainEventType.INVENTORY_ADDED : DomainEventType.INVENTORY_ADJUSTED;
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, eventType, Map.of(
                "productId", product.getId(),
                "warehouseId", warehouse.getId(),
                "quantityChange", request.quantityChange(),
                "reason", request.reason()
        ));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public InventoryResponse getByProductAndWarehouse(UUID productId, UUID warehouseId) {
        InventoryItem inventoryItem = inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Inventory not found"));
        return toResponse(inventoryItem);
    }

    @Transactional
    public List<InventoryReservationAllocation> reserve(UUID productId, int quantity, UUID orderId) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Reservation quantity must be positive");
        }
        List<InventoryItem> lockedInventory = inventoryItemRepository.lockAvailableByProductId(productId);
        int totalAvailable = lockedInventory.stream().mapToInt(InventoryItem::getAvailableQuantity).sum();
        if (totalAvailable < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Insufficient inventory for product: " + productId);
        }

        int remaining = quantity;
        List<InventoryReservationAllocation> allocations = new ArrayList<>();
        for (InventoryItem inventoryItem : lockedInventory) {
            if (remaining == 0) {
                break;
            }
            int allocated = Math.min(inventoryItem.getAvailableQuantity(), remaining);
            inventoryItem.setAvailableQuantity(inventoryItem.getAvailableQuantity() - allocated);
            inventoryItem.setReservedQuantity(inventoryItem.getReservedQuantity() + allocated);
            inventoryItemRepository.save(inventoryItem);

            InventoryReservation reservation = new InventoryReservation();
            reservation.setProductId(productId);
            reservation.setWarehouseId(inventoryItem.getWarehouse().getId());
            reservation.setOrderId(orderId);
            reservation.setQuantity(allocated);
            reservation.setStatus(InventoryReservationStatus.RESERVED);
            reservation.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
            InventoryReservation savedReservation = reservationRepository.save(reservation);
            allocations.add(new InventoryReservationAllocation(savedReservation.getId(), productId, inventoryItem.getWarehouse().getId(), allocated));
            remaining -= allocated;
        }
        domainEventPublisher.publish(productId, AGGREGATE_TYPE, DomainEventType.INVENTORY_RESERVED, Map.of(
                "productId", productId,
                "quantity", quantity,
                "orderId", orderId
        ));
        return allocations;
    }

    @Transactional
    public void releaseByOrderId(UUID orderId) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
                continue;
            }
            InventoryItem inventoryItem = inventoryItemRepository
                    .lockByProductIdAndWarehouseId(reservation.getProductId(), reservation.getWarehouseId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Inventory item not found"));
            inventoryItem.setAvailableQuantity(inventoryItem.getAvailableQuantity() + reservation.getQuantity());
            inventoryItem.setReservedQuantity(inventoryItem.getReservedQuantity() - reservation.getQuantity());
            reservation.setStatus(InventoryReservationStatus.RELEASED);
            inventoryItemRepository.save(inventoryItem);
            reservationRepository.save(reservation);
            domainEventPublisher.publish(inventoryItem.getId(), AGGREGATE_TYPE, DomainEventType.INVENTORY_RELEASED, Map.of(
                    "productId", reservation.getProductId(),
                    "warehouseId", reservation.getWarehouseId(),
                    "quantity", reservation.getQuantity(),
                    "orderId", orderId
            ));
        }
    }

    @Transactional(readOnly = true)
    public long consolidatedAvailable(UUID productId) {
        return inventoryItemRepository.sumAvailableByProductId(productId);
    }

    private InventoryItem createEmptyInventoryItem(UUID productId, Warehouse warehouse) {
        InventoryItem inventoryItem = new InventoryItem();
        inventoryItem.setProductId(productId);
        inventoryItem.setWarehouse(warehouse);
        inventoryItem.setAvailableQuantity(0);
        inventoryItem.setReservedQuantity(0);
        return inventoryItem;
    }

    private InventoryResponse toResponse(InventoryItem inventoryItem) {
        return new InventoryResponse(
                inventoryItem.getProductId(),
                inventoryItem.getWarehouse().getId(),
                inventoryItem.getAvailableQuantity(),
                inventoryItem.getReservedQuantity(),
                inventoryItemRepository.sumAvailableByProductId(inventoryItem.getProductId())
        );
    }
}
