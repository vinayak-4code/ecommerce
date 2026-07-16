package com.acme.ecommerce.seller.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.seller.dto.CreateWarehouseRequest;
import com.acme.ecommerce.seller.dto.UpdateWarehouseRequest;
import com.acme.ecommerce.seller.dto.WarehouseResponse;
import com.acme.ecommerce.seller.enums.WarehouseStatus;
import com.acme.ecommerce.seller.service.WarehouseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for seller warehouse endpoints.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseControllerTest {

    @Mock
    private WarehouseService warehouseService;

    @InjectMocks
    private WarehouseController warehouseController;

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
    void create_shouldDelegateAuthenticatedSellerAndRequestToWarehouseService() {
        // Given
        CreateWarehouseRequest request = createRequest("Main Warehouse", "WH-1");
        WarehouseResponse expected = response(UUID.randomUUID(), "Main Warehouse", "WH-1", WarehouseStatus.ACTIVE);
        when(warehouseService.create(sellerUserId, request)).thenReturn(expected);

        // When
        WarehouseResponse response = warehouseController.create(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(warehouseService).create(sellerUserId, request);
    }

    @Test
    void list_shouldDelegateAuthenticatedSellerAndPaginationToWarehouseService() {
        // Given
        Page<WarehouseResponse> expected = new PageImpl<>(List.of(response(UUID.randomUUID(), "Main Warehouse", "WH-1", WarehouseStatus.ACTIVE)));
        when(warehouseService.list(sellerUserId, 2, 5)).thenReturn(expected);

        // When
        Page<WarehouseResponse> response = warehouseController.list(2, 5);

        // Then
        assertThat(response).isSameAs(expected);
        verify(warehouseService).list(sellerUserId, 2, 5);
    }

    @Test
    void update_shouldDelegateAuthenticatedSellerWarehouseIdAndRequestToWarehouseService() {
        // Given
        UUID warehouseId = UUID.randomUUID();
        UpdateWarehouseRequest request = new UpdateWarehouseRequest("Updated", "2 Main", "St Paul", "MN", "US", "55101", WarehouseStatus.INACTIVE);
        WarehouseResponse expected = response(warehouseId, "Updated", "WH-1", WarehouseStatus.INACTIVE);
        when(warehouseService.update(sellerUserId, warehouseId, request)).thenReturn(expected);

        // When
        WarehouseResponse response = warehouseController.update(warehouseId, request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(warehouseService).update(sellerUserId, warehouseId, request);
    }

    @Test
    void delete_shouldDelegateAuthenticatedSellerAndWarehouseIdToWarehouseService() {
        // Given
        UUID warehouseId = UUID.randomUUID();

        // When
        warehouseController.delete(warehouseId);

        // Then
        verify(warehouseService).delete(sellerUserId, warehouseId);
    }

    private CreateWarehouseRequest createRequest(String name, String code) {
        return new CreateWarehouseRequest(name, code, "1 Main", "Minneapolis", "MN", "US", "55401");
    }

    private WarehouseResponse response(UUID id, String name, String code, WarehouseStatus status) {
        return new WarehouseResponse(id, name, code, "1 Main", "Minneapolis", "MN", "US", "55401", status);
    }
}
