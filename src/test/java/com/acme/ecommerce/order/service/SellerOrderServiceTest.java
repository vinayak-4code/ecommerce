package com.acme.ecommerce.order.service;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.inventory.service.InventoryService;
import com.acme.ecommerce.order.dto.SellerOrderLineResponse;
import com.acme.ecommerce.order.entity.CustomerOrder;
import com.acme.ecommerce.order.entity.OrderLine;
import com.acme.ecommerce.order.enums.FulfillmentStatus;
import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.repository.CustomerOrderRepository;
import com.acme.ecommerce.order.repository.OrderLineRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.service.SellerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * Unit specification for seller order fulfillment operations.
 *
 * <p>Sellers can only see and mutate order lines for their own products. Shipping
 * consumes reserved stock; cancellation releases it back to available inventory.</p>
 */
@ExtendWith(MockitoExtension.class)
class SellerOrderServiceTest {

    @Mock
    private SellerService sellerService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderLineRepository orderLineRepository;

    @Mock
    private CustomerOrderRepository orderRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private SellerOrderService sellerOrderService;

    @Test
    void list_shouldReturnEmptyPage_whenSellerHasNoProducts() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findAllBySellerProfileId(seller.getId())).thenReturn(List.of());

        // When
        Page<SellerOrderLineResponse> response = sellerOrderService.list(sellerUserId, 0, 20);

        // Then
        assertThat(response.getContent()).isEmpty();
    }

    @Test
    void list_shouldReturnSellerOrderLines_whenSellerHasProducts() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Product product = product(UUID.randomUUID());
        OrderLine line = line(product.getId(), FulfillmentStatus.RESERVED);
        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findAllBySellerProfileId(seller.getId())).thenReturn(List.of(product));
        when(orderLineRepository.findSellerLines(eq(java.util.Set.of(product.getId())), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(line)));

        // When
        Page<SellerOrderLineResponse> response = sellerOrderService.list(sellerUserId, -5, 500);

        // Then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).productId()).isEqualTo(product.getId());
    }

    @Test
    void ship_shouldConsumeReservationSetStatusToShippedAndRefreshOrderStatus() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Product product = product(UUID.randomUUID());
        OrderLine line = line(product.getId(), FulfillmentStatus.RESERVED);
        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findAllBySellerProfileId(seller.getId())).thenReturn(List.of(product));
        when(orderLineRepository.findByIdAndProductIdIn(line.getId(), java.util.Set.of(product.getId()))).thenReturn(Optional.of(line));
        when(orderLineRepository.save(line)).thenReturn(line);
        when(orderLineRepository.findByOrderId(line.getOrder().getId())).thenReturn(List.of(line));

        // When
        SellerOrderLineResponse response = sellerOrderService.ship(sellerUserId, line.getId());

        // Then
        assertThat(response.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SHIPPED);
        assertThat(line.getOrder().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(inventoryService).consumeForLine(line.getProductId(), line.getWarehouseId(), line.getQuantity(), line.getOrder().getId());
        verify(orderRepository).save(line.getOrder());
    }

    @Test
    void ship_shouldThrowBusinessException_whenLineIsAlreadyShipped() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Product product = product(UUID.randomUUID());
        OrderLine line = line(product.getId(), FulfillmentStatus.SHIPPED);
        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findAllBySellerProfileId(seller.getId())).thenReturn(List.of(product));
        when(orderLineRepository.findByIdAndProductIdIn(line.getId(), java.util.Set.of(product.getId()))).thenReturn(Optional.of(line));

        // When / Then
        assertThatThrownBy(() -> sellerOrderService.ship(sellerUserId, line.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already shipped");
    }

    @Test
    void cancel_shouldReleaseReservationSetStatusToCancelledAndRefreshOrderStatus() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Product product = product(UUID.randomUUID());
        OrderLine line = line(product.getId(), FulfillmentStatus.RESERVED);
        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findAllBySellerProfileId(seller.getId())).thenReturn(List.of(product));
        when(orderLineRepository.findByIdAndProductIdIn(line.getId(), java.util.Set.of(product.getId()))).thenReturn(Optional.of(line));
        when(orderLineRepository.save(line)).thenReturn(line);
        when(orderLineRepository.findByOrderId(line.getOrder().getId())).thenReturn(List.of(line));

        // When
        SellerOrderLineResponse response = sellerOrderService.cancel(sellerUserId, line.getId());

        // Then
        assertThat(response.fulfillmentStatus()).isEqualTo(FulfillmentStatus.CANCELLED);
        assertThat(line.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(inventoryService).releaseForLine(line.getProductId(), line.getWarehouseId(), line.getQuantity(), line.getOrder().getId());
    }

    @Test
    void cancel_shouldThrowBusinessException_whenLineIsAlreadyShipped() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        Product product = product(UUID.randomUUID());
        OrderLine line = line(product.getId(), FulfillmentStatus.SHIPPED);
        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findAllBySellerProfileId(seller.getId())).thenReturn(List.of(product));
        when(orderLineRepository.findByIdAndProductIdIn(line.getId(), java.util.Set.of(product.getId()))).thenReturn(Optional.of(line));

        // When / Then
        assertThatThrownBy(() -> sellerOrderService.cancel(sellerUserId, line.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void ship_shouldThrowResourceNotFoundException_whenSellerHasNoMatchingProducts() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        SellerProfile seller = seller(UUID.randomUUID());
        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findAllBySellerProfileId(seller.getId())).thenReturn(List.of());

        // When / Then
        assertThatThrownBy(() -> sellerOrderService.ship(sellerUserId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Order line not found for seller");
    }

    private SellerProfile seller(UUID id) {
        SellerProfile seller = new SellerProfile();
        seller.setId(id);
        return seller;
    }

    private Product product(UUID id) {
        Product product = new Product();
        product.setId(id);
        return product;
    }

    private OrderLine line(UUID productId, FulfillmentStatus status) {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(UUID.randomUUID());
        customer.setFullName("Demo Customer");

        CustomerOrder order = new CustomerOrder();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-1");
        order.setCustomerProfile(customer);
        order.setStatus(OrderStatus.PLACED);
        order.setShippingAddress("Ship here");

        OrderLine line = new OrderLine();
        line.setId(UUID.randomUUID());
        line.setOrder(order);
        line.setProductId(productId);
        line.setProductName("Keyboard");
        line.setWarehouseId(UUID.randomUUID());
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("50.00"));
        line.setDiscountAmount(BigDecimal.ZERO);
        line.setTotalAmount(new BigDecimal("100.00"));
        line.setFulfillmentStatus(status);
        return line;
    }
}
