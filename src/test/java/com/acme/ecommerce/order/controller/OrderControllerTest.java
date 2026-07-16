package com.acme.ecommerce.order.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.order.dto.CreateOrderRequest;
import com.acme.ecommerce.order.dto.OrderResponse;
import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.enums.PaymentStatus;
import com.acme.ecommerce.order.service.OrderService;
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
 * Unit specification for the customer order controller.
 *
 * <p>Order orchestration belongs to OrderService; the controller's job is to
 * pass the authenticated customer id and route parameters to the service.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private UUID customerUserId;

    @BeforeEach
    void setUp() {
        customerUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(customerUserId, "customer@example.com", UserRole.CUSTOMER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void placeOrder_shouldDelegateAuthenticatedCustomerAndRequestToOrderService() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest("123 Main Street");
        OrderResponse expected = orderResponse(UUID.randomUUID());
        when(orderService.placeOrder(customerUserId, request)).thenReturn(expected);

        // When
        OrderResponse response = orderController.placeOrder(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(orderService).placeOrder(customerUserId, request);
    }

    @Test
    void get_shouldDelegateAuthenticatedCustomerAndOrderIdToOrderService() {
        // Given
        UUID orderId = UUID.randomUUID();
        OrderResponse expected = orderResponse(orderId);
        when(orderService.get(customerUserId, orderId)).thenReturn(expected);

        // When
        OrderResponse response = orderController.get(orderId);

        // Then
        assertThat(response).isSameAs(expected);
        verify(orderService).get(customerUserId, orderId);
    }

    @Test
    void list_shouldDelegateAuthenticatedCustomerAndPaginationToOrderService() {
        // Given
        Page<OrderResponse> expected = new PageImpl<>(List.of(orderResponse(UUID.randomUUID())));
        when(orderService.list(customerUserId, 1, 25)).thenReturn(expected);

        // When
        Page<OrderResponse> response = orderController.list(1, 25);

        // Then
        assertThat(response).isSameAs(expected);
        verify(orderService).list(customerUserId, 1, 25);
    }

    private OrderResponse orderResponse(UUID orderId) {
        return new OrderResponse(orderId, "ORD-1", OrderStatus.PLACED, PaymentStatus.PENDING, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, "123 Main Street", Instant.now(), List.of());
    }
}
