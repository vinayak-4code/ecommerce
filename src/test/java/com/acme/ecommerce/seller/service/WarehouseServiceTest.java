package com.acme.ecommerce.seller.service;

import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.inventory.repository.InventoryItemRepository;
import com.acme.ecommerce.seller.dto.CreateWarehouseRequest;
import com.acme.ecommerce.seller.dto.UpdateWarehouseRequest;
import com.acme.ecommerce.seller.dto.WarehouseResponse;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import com.acme.ecommerce.seller.enums.WarehouseStatus;
import com.acme.ecommerce.seller.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for seller-owned warehouse management.
 *
 * <p>Warehouses are simple, but ownership and delete safety are important
 * because inventory rows are tied to warehouse ids.</p>
 */
@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private SellerService sellerService;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private InventoryItemRepository inventoryItemRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    void create_shouldNormalizeCodeTrimFieldsAndPersistWarehouseForSeller() {
        // Given
        UUID userId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        CreateWarehouseRequest request = new CreateWarehouseRequest(" Main ", " main ", " Line 1 ", " Bengaluru ", " KA ", " IN ", " 560001 ");
        when(sellerService.requireByUserId(userId)).thenReturn(seller);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> {
            Warehouse warehouse = invocation.getArgument(0);
            warehouse.setId(UUID.randomUUID());
            return warehouse;
        });

        // When
        WarehouseResponse response = warehouseService.create(userId, request);

        // Then
        assertThat(response.name()).isEqualTo("Main");
        assertThat(response.code()).isEqualTo("MAIN");
        assertThat(response.status()).isEqualTo(WarehouseStatus.ACTIVE);
    }

    @Test
    void list_shouldReturnSellerWarehousesWithBoundedPagination() {
        // Given
        UUID userId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Warehouse warehouse = warehouse(UUID.randomUUID(), seller);
        when(sellerService.requireByUserId(userId)).thenReturn(seller);
        when(warehouseRepository.findBySellerProfileId(any(UUID.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(warehouse)));

        // When
        Page<WarehouseResponse> response = warehouseService.list(userId, -5, 500);

        // Then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).id()).isEqualTo(warehouse.getId());
    }

    @Test
    void update_shouldModifySellerOwnedWarehouse_whenWarehouseExists() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Warehouse warehouse = warehouse(warehouseId, seller);
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(" Updated ", " New Line ", " Mysuru ", " KA ", " IN ", " 570001 ", WarehouseStatus.INACTIVE);

        when(sellerService.requireByUserId(userId)).thenReturn(seller);
        when(warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        // When
        WarehouseResponse response = warehouseService.update(userId, warehouseId, request);

        // Then
        assertThat(response.name()).isEqualTo("Updated");
        assertThat(response.status()).isEqualTo(WarehouseStatus.INACTIVE);
    }

    @Test
    void update_shouldThrowResourceNotFoundException_whenWarehouseDoesNotBelongToSeller() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        when(sellerService.requireByUserId(userId)).thenReturn(seller);
        when(warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> warehouseService.update(userId, warehouseId, updateRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Warehouse not found");
    }

    @Test
    void delete_shouldRejectWarehouseDeletion_whenInventoryRowsExist() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Warehouse warehouse = warehouse(warehouseId, seller);
        when(sellerService.requireByUserId(userId)).thenReturn(seller);
        when(warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())).thenReturn(Optional.of(warehouse));
        when(inventoryItemRepository.existsByWarehouseId(warehouseId)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> warehouseService.delete(userId, warehouseId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Warehouse has inventory");
    }

    @Test
    void delete_shouldDeleteWarehouse_whenNoInventoryRowsExist() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Warehouse warehouse = warehouse(warehouseId, seller);
        when(sellerService.requireByUserId(userId)).thenReturn(seller);
        when(warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())).thenReturn(Optional.of(warehouse));
        when(inventoryItemRepository.existsByWarehouseId(warehouseId)).thenReturn(false);

        // When
        warehouseService.delete(userId, warehouseId);

        // Then
        verify(warehouseRepository).delete(warehouse);
    }

    @Test
    void requireSellerWarehouse_shouldReturnWarehouse_whenItBelongsToSeller() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Warehouse warehouse = warehouse(warehouseId, seller);
        when(sellerService.requireByUserId(userId)).thenReturn(seller);
        when(warehouseRepository.findByIdAndSellerProfileId(warehouseId, seller.getId())).thenReturn(Optional.of(warehouse));

        // When
        Warehouse response = warehouseService.requireSellerWarehouse(userId, warehouseId);

        // Then
        assertThat(response).isSameAs(warehouse);
    }

    private UpdateWarehouseRequest updateRequest() {
        return new UpdateWarehouseRequest("Updated", "Line", "City", "State", "Country", "560001", WarehouseStatus.ACTIVE);
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
        warehouse.setName("Main");
        warehouse.setCode("MAIN");
        warehouse.setAddressLine1("Line 1");
        warehouse.setCity("Bengaluru");
        warehouse.setState("KA");
        warehouse.setCountry("IN");
        warehouse.setPostalCode("560001");
        warehouse.setStatus(WarehouseStatus.ACTIVE);
        return warehouse;
    }
}
