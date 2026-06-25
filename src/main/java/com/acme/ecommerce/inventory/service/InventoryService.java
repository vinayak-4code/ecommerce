package com.acme.ecommerce.inventory.service;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ForbiddenOperationException;
import com.acme.ecommerce.inventory.dto.BulkInventoryUpdateRequest;
import com.acme.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.acme.ecommerce.inventory.dto.InventoryResponse;
import com.acme.ecommerce.inventory.dto.InventoryUpdateRequest;
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

/**
 * Warehouse-level inventory service.
 *
 * <p>Seller stock updates use normal PostgreSQL row updates. Purchase reservation
 * uses conditional update statements ({@code available >= requested}) so the
 * final unit can only be reserved once even under concurrent checkout attempts.</p>
 */
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
    public InventoryResponse update(UUID sellerUserId, InventoryUpdateRequest request) {
        Warehouse warehouse = warehouseService.requireSellerWarehouse(sellerUserId, request.warehouseId());
        Product product = productService.requireProduct(request.productId());
        ensureSellerOwnsProductWarehouse(product, warehouse);

        InventoryItem inventoryItem = inventoryItemRepository
                .findByProductIdAndWarehouseId(request.productId(), request.warehouseId())
                .orElseGet(() -> createEmptyInventoryItem(product.getId(), warehouse));
        int previousAvailableQuantity = inventoryItem.getAvailableQuantity();
        inventoryItem.setAvailableQuantity(request.availableQuantity());
        InventoryItem saved = inventoryItemRepository.save(inventoryItem);
        DomainEventType eventType = request.availableQuantity() > previousAvailableQuantity
                ? DomainEventType.INVENTORY_ADDED
                : DomainEventType.INVENTORY_ADJUSTED;
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, eventType, Map.of(
                "productId", product.getId(),
                "warehouseId", warehouse.getId(),
                "previousAvailableQuantity", previousAvailableQuantity,
                "availableQuantity", request.availableQuantity(),
                "reason", request.reason()
        ));
        return toResponse(saved);
    }

    @Transactional
    public List<InventoryResponse> bulkUpdate(UUID sellerUserId, BulkInventoryUpdateRequest request) {
        return request.items().stream()
                .map(item -> update(sellerUserId, item))
                .toList();
    }

    @Transactional
    public InventoryResponse adjust(UUID sellerUserId, InventoryAdjustmentRequest request) {
        if (request.quantityChange() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Inventory quantity change cannot be zero. Use absolute update to set stock to zero.");
        }
        Warehouse warehouse = warehouseService.requireSellerWarehouse(sellerUserId, request.warehouseId());
        Product product = productService.requireProduct(request.productId());
        ensureSellerOwnsProductWarehouse(product, warehouse);
        InventoryItem inventoryItem = inventoryItemRepository
                .findByProductIdAndWarehouseId(request.productId(), request.warehouseId())
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
        int remaining = quantity;
        List<InventoryReservationAllocation> allocations = new ArrayList<>();
        List<InventoryItem> candidates = inventoryItemRepository.findAvailableByProductId(productId);
        for (InventoryItem candidate : candidates) {
            if (remaining == 0) {
                break;
            }
            int requestedFromWarehouse = Math.min(candidate.getAvailableQuantity(), remaining);
            if (requestedFromWarehouse <= 0) {
                continue;
            }
            int updated = inventoryItemRepository.reserveQuantity(productId, candidate.getWarehouse().getId(), requestedFromWarehouse);
            if (updated == 0) {
                continue;
            }
            InventoryReservation savedReservation = reservationRepository.save(toReservation(productId, candidate.getWarehouse().getId(), requestedFromWarehouse, orderId));
            allocations.add(new InventoryReservationAllocation(savedReservation.getId(), productId, candidate.getWarehouse().getId(), requestedFromWarehouse));
            remaining -= requestedFromWarehouse;
        }
        if (remaining > 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Insufficient inventory for product: " + productId);
        }
        domainEventPublisher.publish(productId, AGGREGATE_TYPE, DomainEventType.INVENTORY_RESERVED, Map.of(
                "productId", productId,
                "quantity", quantity,
                "orderId", orderId
        ));
        return allocations;
    }

    @Transactional
    public void consumeByOrderId(UUID orderId) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
                continue;
            }
            int updated = inventoryItemRepository.consumeReservedQuantity(reservation.getProductId(), reservation.getWarehouseId(), reservation.getQuantity());
            if (updated == 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Reserved inventory could not be consumed");
            }
            reservation.setStatus(InventoryReservationStatus.CONSUMED);
            reservationRepository.save(reservation);
            domainEventPublisher.publish(reservation.getProductId(), AGGREGATE_TYPE, DomainEventType.INVENTORY_CONSUMED, Map.of(
                    "productId", reservation.getProductId(),
                    "warehouseId", reservation.getWarehouseId(),
                    "quantity", reservation.getQuantity(),
                    "orderId", orderId
            ));
        }
    }

    @Transactional
    public void releaseByOrderId(UUID orderId) {
        List<InventoryReservation> reservations = reservationRepository.findByOrderId(orderId);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() != InventoryReservationStatus.RESERVED) {
                continue;
            }
            int updated = inventoryItemRepository.releaseQuantity(reservation.getProductId(), reservation.getWarehouseId(), reservation.getQuantity());
            if (updated == 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Reserved inventory could not be released");
            }
            reservation.setStatus(InventoryReservationStatus.RELEASED);
            reservationRepository.save(reservation);
            domainEventPublisher.publish(reservation.getProductId(), AGGREGATE_TYPE, DomainEventType.INVENTORY_RELEASED, Map.of(
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

    private void ensureSellerOwnsProductWarehouse(Product product, Warehouse warehouse) {
        if (!product.getSellerProfile().getId().equals(warehouse.getSellerProfile().getId())) {
            throw new ForbiddenOperationException("Seller cannot manage inventory for another seller product or warehouse");
        }
    }

    private InventoryReservation toReservation(UUID productId, UUID warehouseId, int quantity, UUID orderId) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setProductId(productId);
        reservation.setWarehouseId(warehouseId);
        reservation.setOrderId(orderId);
        reservation.setQuantity(quantity);
        reservation.setStatus(InventoryReservationStatus.RESERVED);
        reservation.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        return reservation;
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
