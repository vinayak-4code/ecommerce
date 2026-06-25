package com.acme.ecommerce.inventory;

import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.inventory.entity.InventoryItem;
import com.acme.ecommerce.inventory.entity.InventoryReservation;
import com.acme.ecommerce.inventory.repository.InventoryItemRepository;
import com.acme.ecommerce.inventory.repository.InventoryReservationRepository;
import com.acme.ecommerce.inventory.service.InventoryReservationAllocation;
import com.acme.ecommerce.inventory.service.InventoryService;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import com.acme.ecommerce.seller.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private ProductService productService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Test
    void reserveFailsWhenRequestedQuantityExceedsAvailability() {
        InventoryService inventoryService = new InventoryService(inventoryItemRepository, reservationRepository, warehouseService, productService, domainEventPublisher);
        UUID productId = UUID.randomUUID();
        when(inventoryItemRepository.findAvailableByProductId(productId)).thenReturn(List.of());

        assertThatThrownBy(() -> inventoryService.reserve(productId, 2, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient inventory");
    }

    @Test
    void reserveUsesConditionalUpdateSoLastUnitCanBeReservedOnce() {
        InventoryService inventoryService = new InventoryService(inventoryItemRepository, reservationRepository, warehouseService, productService, domainEventPublisher);
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem inventoryItem = inventory(productId, warehouseId, 1, 0);
        when(inventoryItemRepository.findAvailableByProductId(productId)).thenReturn(List.of(inventoryItem));
        when(inventoryItemRepository.reserveQuantity(productId, warehouseId, 1)).thenReturn(1);
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(invocation -> {
            InventoryReservation reservation = invocation.getArgument(0);
            reservation.setId(UUID.randomUUID());
            return reservation;
        });

        List<InventoryReservationAllocation> allocations = inventoryService.reserve(productId, 1, orderId);

        assertThat(allocations).hasSize(1);
        assertThat(allocations.getFirst().warehouseId()).isEqualTo(warehouseId);
        verify(inventoryItemRepository).reserveQuantity(productId, warehouseId, 1);
        verify(reservationRepository).save(any(InventoryReservation.class));
        verify(domainEventPublisher).publish(eq(productId), eq("Inventory"), any(), any());
    }

    private InventoryItem inventory(UUID productId, UUID warehouseId, int available, int reserved) {
        SellerProfile seller = new SellerProfile();
        seller.setId(UUID.randomUUID());
        Warehouse warehouse = new Warehouse();
        warehouse.setId(warehouseId);
        warehouse.setSellerProfile(seller);
        InventoryItem item = new InventoryItem();
        item.setId(UUID.randomUUID());
        item.setProductId(productId);
        item.setWarehouse(warehouse);
        item.setAvailableQuantity(available);
        item.setReservedQuantity(reserved);
        return item;
    }
}
