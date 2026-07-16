package com.acme.ecommerce.inventory.service;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ForbiddenOperationException;
import com.acme.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.acme.ecommerce.inventory.dto.InventoryResponse;
import com.acme.ecommerce.inventory.dto.InventoryUpdateRequest;
import com.acme.ecommerce.inventory.entity.InventoryItem;
import com.acme.ecommerce.inventory.entity.InventoryReservation;
import com.acme.ecommerce.inventory.enums.InventoryAdjustmentReason;
import com.acme.ecommerce.inventory.enums.InventoryReservationStatus;
import com.acme.ecommerce.inventory.repository.InventoryItemRepository;
import com.acme.ecommerce.inventory.repository.InventoryReservationRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import com.acme.ecommerce.seller.service.SellerService;
import com.acme.ecommerce.seller.service.WarehouseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for seller inventory management and reservation semantics.
 *
 * <p>The tests cover absolute updates, deltas, ownership, conditional reservation,
 * release, consumption, and consolidated availability without a database.</p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private SellerService sellerService;

    @Mock
    private ProductService productService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void update_shouldCreateInventoryItemAndPublishInventoryAdded_whenNoExistingRowExists() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(sellerUserId);
        Warehouse warehouse = warehouse(warehouseId, seller);
        Product product = product(productId, seller);
        InventoryUpdateRequest request = new InventoryUpdateRequest(productId, warehouseId, 25, InventoryAdjustmentReason.INITIAL_STOCK);

        when(warehouseService.requireSellerWarehouse(sellerUserId, warehouseId)).thenReturn(warehouse);
        when(productService.requireProduct(productId)).thenReturn(product);
        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.empty());
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(invocation -> {
            InventoryItem item = invocation.getArgument(0);
            item.setId(UUID.randomUUID());
            return item;
        });
        when(inventoryItemRepository.sumAvailableByProductId(productId)).thenReturn(25L);

        // When
        InventoryResponse response = inventoryService.update(sellerUserId, request);

        // Then
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.warehouseId()).isEqualTo(warehouseId);
        assertThat(response.availableQuantity()).isEqualTo(25);
        assertThat(response.consolidatedAvailableQuantity()).isEqualTo(25L);
        verify(domainEventPublisher).publish(any(UUID.class), eq("Inventory"), eq(DomainEventType.INVENTORY_ADDED), any());
    }

    @Test
    void update_shouldPublishInventoryAdjusted_whenAvailableQuantityDoesNotIncrease() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(sellerUserId);
        Warehouse warehouse = warehouse(warehouseId, seller);
        Product product = product(productId, seller);
        InventoryItem existing = inventoryItem(UUID.randomUUID(), productId, warehouse, 20, 0);
        InventoryUpdateRequest request = new InventoryUpdateRequest(productId, warehouseId, 5, InventoryAdjustmentReason.STOCK_CORRECTION);

        when(warehouseService.requireSellerWarehouse(sellerUserId, warehouseId)).thenReturn(warehouse);
        when(productService.requireProduct(productId)).thenReturn(product);
        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(existing));
        when(inventoryItemRepository.save(existing)).thenReturn(existing);
        when(inventoryItemRepository.sumAvailableByProductId(productId)).thenReturn(5L);

        // When
        InventoryResponse response = inventoryService.update(sellerUserId, request);

        // Then
        assertThat(response.availableQuantity()).isEqualTo(5);
        verify(domainEventPublisher).publish(existing.getId(), "Inventory", DomainEventType.INVENTORY_ADJUSTED, java.util.Map.of(
                "productId", productId,
                "warehouseId", warehouseId,
                "previousAvailableQuantity", 20,
                "availableQuantity", 5,
                "reason", InventoryAdjustmentReason.STOCK_CORRECTION
        ));
    }

    @Test
    void update_shouldRejectRequest_whenProductAndWarehouseBelongToDifferentSellers() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = warehouse(warehouseId, seller(UUID.randomUUID()));
        Product product = product(productId, seller(UUID.randomUUID()));
        InventoryUpdateRequest request = new InventoryUpdateRequest(productId, warehouseId, 10, InventoryAdjustmentReason.INITIAL_STOCK);

        when(warehouseService.requireSellerWarehouse(sellerUserId, warehouseId)).thenReturn(warehouse);
        when(productService.requireProduct(productId)).thenReturn(product);

        // When / Then
        assertThatThrownBy(() -> inventoryService.update(sellerUserId, request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("another seller product or warehouse");
    }

    @Test
    void adjust_shouldRejectZeroQuantityChange() {
        // Given
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(UUID.randomUUID(), UUID.randomUUID(), 0, InventoryAdjustmentReason.STOCK_CORRECTION);

        // When / Then
        assertThatThrownBy(() -> inventoryService.adjust(UUID.randomUUID(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quantity change cannot be zero");
    }

    @Test
    void adjust_shouldRejectNegativeAvailableQuantity() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(sellerUserId);
        Warehouse warehouse = warehouse(warehouseId, seller);
        Product product = product(productId, seller);
        InventoryItem existing = inventoryItem(UUID.randomUUID(), productId, warehouse, 3, 0);
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(productId, warehouseId, -5, InventoryAdjustmentReason.DAMAGED_STOCK);

        when(warehouseService.requireSellerWarehouse(sellerUserId, warehouseId)).thenReturn(warehouse);
        when(productService.requireProduct(productId)).thenReturn(product);
        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(existing));

        // When / Then
        assertThatThrownBy(() -> inventoryService.adjust(sellerUserId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("would make available quantity negative");
    }

    @Test
    void reserve_shouldAllocateQuantityAcrossWarehousesAndPublishReservedEvent() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Warehouse warehouse1 = warehouse(UUID.randomUUID(), seller(UUID.randomUUID()));
        Warehouse warehouse2 = warehouse(UUID.randomUUID(), seller(UUID.randomUUID()));
        InventoryItem item1 = inventoryItem(UUID.randomUUID(), productId, warehouse1, 2, 0);
        InventoryItem item2 = inventoryItem(UUID.randomUUID(), productId, warehouse2, 5, 0);

        when(inventoryItemRepository.findAvailableByProductId(productId)).thenReturn(List.of(item1, item2));
        when(inventoryItemRepository.reserveQuantity(productId, warehouse1.getId(), 2)).thenReturn(1);
        when(inventoryItemRepository.reserveQuantity(productId, warehouse2.getId(), 3)).thenReturn(1);
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(invocation -> {
            InventoryReservation reservation = invocation.getArgument(0);
            reservation.setId(UUID.randomUUID());
            return reservation;
        });

        // When
        List<InventoryReservationAllocation> allocations = inventoryService.reserve(productId, 5, orderId);

        // Then
        assertThat(allocations).hasSize(2);
        assertThat(allocations).extracting(InventoryReservationAllocation::quantity).containsExactly(2, 3);
        verify(domainEventPublisher).publish(eq(productId), eq("Inventory"), eq(DomainEventType.INVENTORY_RESERVED), any());
    }

    @Test
    void reserve_shouldThrowBusinessException_whenRequestedQuantityCannotBeFullyReserved() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Warehouse warehouse = warehouse(UUID.randomUUID(), seller(UUID.randomUUID()));
        InventoryItem item = inventoryItem(UUID.randomUUID(), productId, warehouse, 1, 0);

        when(inventoryItemRepository.findAvailableByProductId(productId)).thenReturn(List.of(item));
        when(inventoryItemRepository.reserveQuantity(productId, warehouse.getId(), 1)).thenReturn(1);
        when(reservationRepository.save(any(InventoryReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When / Then
        assertThatThrownBy(() -> inventoryService.reserve(productId, 3, orderId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient inventory");
    }

    @Test
    void consumeByOrderId_shouldConsumeOnlyReservedReservationsAndPublishConsumedEvent() {
        // Given
        UUID orderId = UUID.randomUUID();
        InventoryReservation reserved = reservation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, orderId, InventoryReservationStatus.RESERVED);
        InventoryReservation released = reservation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, orderId, InventoryReservationStatus.RELEASED);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(reserved, released));
        when(inventoryItemRepository.consumeReservedQuantity(reserved.getProductId(), reserved.getWarehouseId(), reserved.getQuantity())).thenReturn(1);

        // When
        inventoryService.consumeByOrderId(orderId);

        // Then
        assertThat(reserved.getStatus()).isEqualTo(InventoryReservationStatus.CONSUMED);
        assertThat(released.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        verify(reservationRepository).save(reserved);
        verify(domainEventPublisher).publish(eq(reserved.getProductId()), eq("Inventory"), eq(DomainEventType.INVENTORY_CONSUMED), any());
    }

    @Test
    void releaseByOrderId_shouldReleaseReservedReservationsBackToAvailableInventory() {
        // Given
        UUID orderId = UUID.randomUUID();
        InventoryReservation reserved = reservation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, orderId, InventoryReservationStatus.RESERVED);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(reserved));
        when(inventoryItemRepository.releaseQuantity(reserved.getProductId(), reserved.getWarehouseId(), reserved.getQuantity())).thenReturn(1);

        // When
        inventoryService.releaseByOrderId(orderId);

        // Then
        assertThat(reserved.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        verify(domainEventPublisher).publish(eq(reserved.getProductId()), eq("Inventory"), eq(DomainEventType.INVENTORY_RELEASED), any());
    }

    @Test
    void restockConsumedLine_shouldAddAvailableQuantityAndPublishReleasedEvent_whenInventoryExists() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryItem item = inventoryItem(UUID.randomUUID(), productId, warehouse(warehouseId, seller(UUID.randomUUID())), 4, 0);
        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.save(item)).thenReturn(item);

        // When
        inventoryService.restockConsumedLine(productId, warehouseId, 3, orderId);

        // Then
        assertThat(item.getAvailableQuantity()).isEqualTo(7);
        verify(domainEventPublisher).publish(eq(item.getId()), eq("Inventory"), eq(DomainEventType.INVENTORY_RELEASED), any());
    }

    @Test
    void consolidatedAvailable_shouldReturnRepositorySum() {
        // Given
        UUID productId = UUID.randomUUID();
        when(inventoryItemRepository.sumAvailableByProductId(productId)).thenReturn(42L);

        // When
        long available = inventoryService.consolidatedAvailable(productId);

        // Then
        assertThat(available).isEqualTo(42L);
    }


    @Test
    void listForSeller_shouldMapAllInventoryRowsForSeller() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Warehouse warehouse = warehouse(UUID.randomUUID(), seller);
        InventoryItem item = inventoryItem(UUID.randomUUID(), UUID.randomUUID(), warehouse, 8, 2);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(inventoryItemRepository.findBySellerId(seller.getId())).thenReturn(List.of(item));
        when(inventoryItemRepository.sumAvailableByProductId(item.getProductId())).thenReturn(8L);

        // When
        List<InventoryResponse> responses = inventoryService.listForSeller(sellerUserId);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).productId()).isEqualTo(item.getProductId());
        assertThat(responses.get(0).warehouseId()).isEqualTo(warehouse.getId());
        assertThat(responses.get(0).availableQuantity()).isEqualTo(8);
        assertThat(responses.get(0).reservedQuantity()).isEqualTo(2);
    }

    @Test
    void hasInventoryForWarehouse_shouldReturnRepositoryResult() {
        // Given
        UUID warehouseId = UUID.randomUUID();
        when(inventoryItemRepository.existsByWarehouseId(warehouseId)).thenReturn(true);

        // When
        boolean result = inventoryService.hasInventoryForWarehouse(warehouseId);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void getByProductAndWarehouse_shouldReturnInventoryResponse_whenInventoryExists() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryItem item = inventoryItem(UUID.randomUUID(), productId, warehouse(warehouseId, seller(UUID.randomUUID())), 6, 1);

        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.of(item));
        when(inventoryItemRepository.sumAvailableByProductId(productId)).thenReturn(6L);

        // When
        InventoryResponse response = inventoryService.getByProductAndWarehouse(productId, warehouseId);

        // Then
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.warehouseId()).isEqualTo(warehouseId);
        assertThat(response.availableQuantity()).isEqualTo(6);
        assertThat(response.reservedQuantity()).isEqualTo(1);
    }

    @Test
    void getByProductAndWarehouse_shouldThrowBusinessException_whenInventoryDoesNotExist() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> inventoryService.getByProductAndWarehouse(productId, warehouseId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Inventory not found");
    }

    @Test
    void reserve_shouldRejectNonPositiveQuantity() {
        // Given
        UUID productId = UUID.randomUUID();

        // When / Then
        assertThatThrownBy(() -> inventoryService.reserve(productId, 0, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reservation quantity must be positive");
    }

    @Test
    void consumeForLine_shouldConsumeOnlyMatchingReservedLine() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryReservation matching = reservation(UUID.randomUUID(), productId, warehouseId, 2, orderId, InventoryReservationStatus.RESERVED);
        InventoryReservation otherWarehouse = reservation(UUID.randomUUID(), productId, UUID.randomUUID(), 2, orderId, InventoryReservationStatus.RESERVED);

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(matching, otherWarehouse));
        when(inventoryItemRepository.consumeReservedQuantity(productId, warehouseId, 2)).thenReturn(1);

        // When
        inventoryService.consumeForLine(productId, warehouseId, 2, orderId);

        // Then
        assertThat(matching.getStatus()).isEqualTo(InventoryReservationStatus.CONSUMED);
        assertThat(otherWarehouse.getStatus()).isEqualTo(InventoryReservationStatus.RESERVED);
        verify(reservationRepository).save(matching);
        verify(domainEventPublisher).publish(eq(productId), eq("Inventory"), eq(DomainEventType.INVENTORY_CONSUMED), any());
    }

    @Test
    void releaseForLine_shouldReleaseOnlyMatchingReservedLine() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        InventoryReservation matching = reservation(UUID.randomUUID(), productId, warehouseId, 2, orderId, InventoryReservationStatus.RESERVED);
        InventoryReservation consumed = reservation(UUID.randomUUID(), productId, warehouseId, 2, orderId, InventoryReservationStatus.CONSUMED);

        when(reservationRepository.findByOrderId(orderId)).thenReturn(List.of(matching, consumed));
        when(inventoryItemRepository.releaseQuantity(productId, warehouseId, 2)).thenReturn(1);

        // When
        inventoryService.releaseForLine(productId, warehouseId, 2, orderId);

        // Then
        assertThat(matching.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
        assertThat(consumed.getStatus()).isEqualTo(InventoryReservationStatus.CONSUMED);
        verify(reservationRepository).save(matching);
        verify(domainEventPublisher).publish(eq(productId), eq("Inventory"), eq(DomainEventType.INVENTORY_RELEASED), any());
    }

    @Test
    void restockConsumedLine_shouldRejectNonPositiveQuantity() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        // When / Then
        assertThatThrownBy(() -> inventoryService.restockConsumedLine(productId, warehouseId, 0, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Restock quantity must be positive");
    }

    @Test
    void restockConsumedLine_shouldThrowBusinessException_whenInventoryDoesNotExist() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(inventoryItemRepository.findByProductIdAndWarehouseId(productId, warehouseId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> inventoryService.restockConsumedLine(productId, warehouseId, 1, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Inventory not found for restock");
    }

    private SellerProfile seller(UUID id) {
        SellerProfile seller = new SellerProfile();
        seller.setId(id);
        return seller;
    }

    private Warehouse warehouse(UUID id, SellerProfile seller) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setSellerProfile(seller);
        warehouse.setName("Main Warehouse");
        warehouse.setCode("MAIN");
        return warehouse;
    }

    private Product product(UUID id, SellerProfile seller) {
        Product product = new Product();
        product.setId(id);
        product.setSellerProfile(seller);
        return product;
    }

    private InventoryItem inventoryItem(UUID id, UUID productId, Warehouse warehouse, int available, int reserved) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setProductId(productId);
        item.setWarehouse(warehouse);
        item.setAvailableQuantity(available);
        item.setReservedQuantity(reserved);
        return item;
    }

    private InventoryReservation reservation(UUID id, UUID productId, UUID warehouseId, int quantity, UUID orderId, InventoryReservationStatus status) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setId(id);
        reservation.setProductId(productId);
        reservation.setWarehouseId(warehouseId);
        reservation.setQuantity(quantity);
        reservation.setOrderId(orderId);
        reservation.setStatus(status);
        return reservation;
    }
}
