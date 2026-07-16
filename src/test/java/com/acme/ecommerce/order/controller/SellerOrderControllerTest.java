package com.acme.ecommerce.order.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.order.dto.SellerOrderLineResponse;
import com.acme.ecommerce.order.enums.FulfillmentStatus;
import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.service.SellerOrderService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for seller order-line endpoints.
 */
@ExtendWith(MockitoExtension.class)
class SellerOrderControllerTest {

    @Mock
    private SellerOrderService sellerOrderService;

    @InjectMocks
    private SellerOrderController sellerOrderController;

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
    void list_shouldDelegateAuthenticatedSellerAndPaginationToSellerOrderService() {
        // Given
        Page<SellerOrderLineResponse> expected = new PageImpl<>(List.of(sellerOrderLineResponse(UUID.randomUUID(), FulfillmentStatus.RESERVED)));
        when(sellerOrderService.list(sellerUserId, 1, 10)).thenReturn(expected);

        // When
        Page<SellerOrderLineResponse> response = sellerOrderController.list(1, 10);

        // Then
        assertThat(response).isSameAs(expected);
        verify(sellerOrderService).list(sellerUserId, 1, 10);
    }

    @Test
    void ship_shouldDelegateAuthenticatedSellerAndLineIdToSellerOrderService() {
        // Given
        UUID lineId = UUID.randomUUID();
        SellerOrderLineResponse expected = sellerOrderLineResponse(lineId, FulfillmentStatus.SHIPPED);
        when(sellerOrderService.ship(sellerUserId, lineId)).thenReturn(expected);

        // When
        SellerOrderLineResponse response = sellerOrderController.ship(lineId);

        // Then
        assertThat(response).isSameAs(expected);
        verify(sellerOrderService).ship(sellerUserId, lineId);
    }

    @Test
    void cancel_shouldDelegateAuthenticatedSellerAndLineIdToSellerOrderService() {
        // Given
        UUID lineId = UUID.randomUUID();
        SellerOrderLineResponse expected = sellerOrderLineResponse(lineId, FulfillmentStatus.CANCELLED);
        when(sellerOrderService.cancel(sellerUserId, lineId)).thenReturn(expected);

        // When
        SellerOrderLineResponse response = sellerOrderController.cancel(lineId);

        // Then
        assertThat(response).isSameAs(expected);
        verify(sellerOrderService).cancel(sellerUserId, lineId);
    }

    private SellerOrderLineResponse sellerOrderLineResponse(UUID lineId, FulfillmentStatus status) {
        return new SellerOrderLineResponse(
                UUID.randomUUID(),
                "ORD-1",
                OrderStatus.PLACED,
                Instant.now(),
                "Jane Customer",
                "123 Main St",
                lineId,
                UUID.randomUUID(),
                "Keyboard",
                UUID.randomUUID(),
                2,
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                status
        );
    }
}
