package com.acme.ecommerce.inventory.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.inventory.dto.BulkInventoryUpdateRequest;
import com.acme.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.acme.ecommerce.inventory.dto.InventoryResponse;
import com.acme.ecommerce.inventory.dto.InventoryUpdateRequest;
import com.acme.ecommerce.inventory.enums.InventoryAdjustmentReason;
import com.acme.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for the seller inventory controller.
 *
 * <p>The controller reads the current seller user id and delegates all inventory
 * update, bulk update, adjustment, and lookup behavior to InventoryService.</p>
 */
@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    private UUID sellerUserId;

    @BeforeEach
    void setUp() {
        sellerUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(sellerUserId, "seller@example.com", UserRole.SELLER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldDelegateAuthenticatedSellerToInventoryService() {
        // Given
        List<InventoryResponse> expected = List.of(inventoryResponse());
        when(inventoryService.listForSeller(sellerUserId)).thenReturn(expected);

        // When
        List<InventoryResponse> response = inventoryController.list();

        // Then
        assertThat(response).isSameAs(expected);
        verify(inventoryService).listForSeller(sellerUserId);
    }

    @Test
    void update_shouldDelegateAuthenticatedSellerAndRequestToInventoryService() {
        // Given
        InventoryUpdateRequest request = new InventoryUpdateRequest(UUID.randomUUID(), UUID.randomUUID(), 10, InventoryAdjustmentReason.INITIAL_STOCK);
        InventoryResponse expected = inventoryResponse();
        when(inventoryService.update(sellerUserId, request)).thenReturn(expected);

        // When
        InventoryResponse response = inventoryController.update(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(inventoryService).update(sellerUserId, request);
    }

    @Test
    void bulkUpdate_shouldDelegateAuthenticatedSellerAndRequestToInventoryService() {
        // Given
        InventoryUpdateRequest item = new InventoryUpdateRequest(UUID.randomUUID(), UUID.randomUUID(), 10, InventoryAdjustmentReason.INITIAL_STOCK);
        BulkInventoryUpdateRequest request = new BulkInventoryUpdateRequest(List.of(item));
        List<InventoryResponse> expected = List.of(inventoryResponse());
        when(inventoryService.bulkUpdate(sellerUserId, request)).thenReturn(expected);

        // When
        List<InventoryResponse> response = inventoryController.bulkUpdate(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(inventoryService).bulkUpdate(sellerUserId, request);
    }

    @Test
    void adjust_shouldDelegateAuthenticatedSellerAndRequestToInventoryService() {
        // Given
        InventoryAdjustmentRequest request = new InventoryAdjustmentRequest(UUID.randomUUID(), UUID.randomUUID(), -2, InventoryAdjustmentReason.DAMAGED_STOCK);
        InventoryResponse expected = inventoryResponse();
        when(inventoryService.adjust(sellerUserId, request)).thenReturn(expected);

        // When
        InventoryResponse response = inventoryController.adjust(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(inventoryService).adjust(sellerUserId, request);
    }

    @Test
    void get_shouldDelegateProductIdAndWarehouseIdToInventoryService() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryResponse expected = inventoryResponse();
        when(inventoryService.getByProductAndWarehouse(productId, warehouseId)).thenReturn(expected);

        // When
        InventoryResponse response = inventoryController.get(productId, warehouseId);

        // Then
        assertThat(response).isSameAs(expected);
        verify(inventoryService).getByProductAndWarehouse(productId, warehouseId);
    }

    private InventoryResponse inventoryResponse() {
        return new InventoryResponse(UUID.randomUUID(), UUID.randomUUID(), 10, 0, 10L);
    }
}
