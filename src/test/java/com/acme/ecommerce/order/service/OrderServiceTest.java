package com.acme.ecommerce.order.service;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.cart.service.CartPricingResult;
import com.acme.ecommerce.cart.service.CartPricingService;
import com.acme.ecommerce.cart.service.CartService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.service.CustomerProfileService;
import com.acme.ecommerce.inventory.service.InventoryReservationAllocation;
import com.acme.ecommerce.inventory.service.InventoryService;
import com.acme.ecommerce.order.dto.CreateOrderRequest;
import com.acme.ecommerce.order.dto.OrderResponse;
import com.acme.ecommerce.order.entity.CustomerOrder;
import com.acme.ecommerce.order.entity.OrderLine;
import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.repository.CustomerOrderRepository;
import com.acme.ecommerce.order.repository.OrderLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
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
 * Unit specification for customer order orchestration.
 *
 * <p>Order placement reprices the cart, verifies stock, reserves inventory,
 * creates header/line rows, clears the cart, and emits an order event.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private CartService cartService;

    @Mock
    private CartPricingService cartPricingService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private OrderLineRepository orderLineRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private OrderService orderService;

    @Test
    void placeOrder_shouldCreatePlacedOrderReserveInventoryClearCartAndPublishEvent_whenCartIsCheckoutReady() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        CustomerProfile customer = customer(customerId);
        Cart cart = cart(cartId, customer);
        CartPricingResult pricing = pricingResult(cartId, productId, 2, CartItemStockStatus.IN_STOCK);
        InventoryReservationAllocation allocation = new InventoryReservationAllocation(UUID.randomUUID(), productId, warehouseId, 2);

        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(cartService.getOrCreateActiveCart(customerUserId)).thenReturn(cart);
        when(cartPricingService.price(cart)).thenReturn(pricing);
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> {
            CustomerOrder order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(UUID.randomUUID());
            }
            return order;
        });
        when(inventoryService.reserve(eq(productId), eq(2), any(UUID.class))).thenReturn(List.of(allocation));

        // When
        OrderResponse response = orderService.placeOrder(customerUserId, new CreateOrderRequest("Ship here"));

        // Then
        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.subtotalAmount()).isEqualByComparingTo("100.00");
        assertThat(response.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("90.00");
        assertThat(response.lines()).hasSize(1);

        ArgumentCaptor<List<OrderLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderLineRepository).saveAll(linesCaptor.capture());
        assertThat(linesCaptor.getValue()).hasSize(1);
        assertThat(linesCaptor.getValue().get(0).getWarehouseId()).isEqualTo(warehouseId);
        assertThat(linesCaptor.getValue().get(0).getTotalAmount()).isEqualByComparingTo("90.00");
        verify(cartService).markOrderedAndClear(cart);
        verify(domainEventPublisher).publish(any(UUID.class), eq("Order"), eq(DomainEventType.ORDER_CREATED), any());
    }

    @Test
    void placeOrder_shouldThrowBusinessException_whenCartIsEmpty() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        CustomerProfile customer = customer(UUID.randomUUID());
        Cart cart = cart(UUID.randomUUID(), customer);
        CartPricingResult emptyPricing = new CartPricingResult(cart.getId(), List.of(), null, false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(cartService.getOrCreateActiveCart(customerUserId)).thenReturn(cart);
        when(cartPricingService.price(cart)).thenReturn(emptyPricing);

        // When / Then
        assertThatThrownBy(() -> orderService.placeOrder(customerUserId, new CreateOrderRequest("Ship here")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot place order for empty cart");
    }

    @Test
    void placeOrder_shouldThrowBusinessException_whenCartIsNotCheckoutReady() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CustomerProfile customer = customer(UUID.randomUUID());
        Cart cart = cart(UUID.randomUUID(), customer);
        CartPricingResult pricing = pricingResult(cart.getId(), productId, 2, CartItemStockStatus.INSUFFICIENT_STOCK);

        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(cartService.getOrCreateActiveCart(customerUserId)).thenReturn(cart);
        when(cartPricingService.price(cart)).thenReturn(pricing);

        // When / Then
        assertThatThrownBy(() -> orderService.placeOrder(customerUserId, new CreateOrderRequest("Ship here")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cart contains out-of-stock or insufficient-stock items");
    }

    @Test
    void get_shouldReturnCustomerOwnedOrderWithLines_whenOrderExists() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CustomerProfile customer = customer(customerId);
        CustomerOrder order = order(orderId, customer);
        OrderLine line = orderLine(order, UUID.randomUUID(), UUID.randomUUID(), 1);

        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(orderRepository.findByIdAndCustomerProfileId(orderId, customerId)).thenReturn(Optional.of(order));
        when(orderLineRepository.findByOrderId(orderId)).thenReturn(List.of(line));

        // When
        OrderResponse response = orderService.get(customerUserId, orderId);

        // Then
        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.lines()).hasSize(1);
    }

    @Test
    void get_shouldThrowResourceNotFoundException_whenOrderDoesNotBelongToCustomer() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CustomerProfile customer = customer(UUID.randomUUID());
        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(orderRepository.findByIdAndCustomerProfileId(orderId, customer.getId())).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> orderService.get(customerUserId, orderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void list_shouldReturnCustomerOrdersWithLinesAndBoundedPagination() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        CustomerProfile customer = customer(UUID.randomUUID());
        CustomerOrder order = order(UUID.randomUUID(), customer);
        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(orderRepository.findByCustomerProfileId(eq(customer.getId()), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(orderLineRepository.findByOrderId(order.getId())).thenReturn(List.of());

        // When
        Page<OrderResponse> response = orderService.list(customerUserId, -5, 500);

        // Then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).id()).isEqualTo(order.getId());
    }

    private CartPricingResult pricingResult(UUID cartId, UUID productId, int quantity, CartItemStockStatus stockStatus) {
        CartPricingResult.CartLinePrice line = new CartPricingResult.CartLinePrice(
                productId,
                "Keyboard",
                quantity,
                stockStatus == CartItemStockStatus.IN_STOCK ? 10 : 1,
                stockStatus,
                new BigDecimal("50.00"),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("90.00")
        );
        boolean checkoutReady = stockStatus == CartItemStockStatus.IN_STOCK;
        return new CartPricingResult(cartId, List.of(line), "SAVE10", checkoutReady, new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"));
    }

    private CustomerProfile customer(UUID id) {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(id);
        customer.setFullName("Demo Customer");
        return customer;
    }

    private Cart cart(UUID id, CustomerProfile customer) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setCustomerProfile(customer);
        return cart;
    }

    private CustomerOrder order(UUID id, CustomerProfile customer) {
        CustomerOrder order = new CustomerOrder();
        order.setId(id);
        order.setOrderNumber("ORD-1");
        order.setCustomerProfile(customer);
        order.setStatus(OrderStatus.PLACED);
        order.setSubtotalAmount(new BigDecimal("100.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTaxAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setShippingAddress("Ship here");
        return order;
    }

    private OrderLine orderLine(CustomerOrder order, UUID productId, UUID warehouseId, int quantity) {
        OrderLine line = new OrderLine();
        line.setId(UUID.randomUUID());
        line.setOrder(order);
        line.setProductId(productId);
        line.setProductName("Keyboard");
        line.setWarehouseId(warehouseId);
        line.setQuantity(quantity);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setSubtotalAmount(new BigDecimal("100.00"));
        line.setDiscountAmount(BigDecimal.ZERO);
        line.setTaxAmount(BigDecimal.ZERO);
        line.setTotalAmount(new BigDecimal("100.00"));
        return line;
    }
}
