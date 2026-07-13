package com.acme.ecommerce.order.service;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.service.CartPricingResult;
import com.acme.ecommerce.cart.service.CartPricingService;
import com.acme.ecommerce.cart.service.CartService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.service.CustomerProfileService;
import com.acme.ecommerce.inventory.service.InventoryReservationAllocation;
import com.acme.ecommerce.inventory.service.InventoryService;
import com.acme.ecommerce.order.dto.CreateOrderRequest;
import com.acme.ecommerce.order.dto.OrderLineResponse;
import com.acme.ecommerce.order.dto.OrderResponse;
import com.acme.ecommerce.order.entity.CustomerOrder;
import com.acme.ecommerce.order.entity.OrderLine;
import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.repository.CustomerOrderRepository;
import com.acme.ecommerce.order.repository.OrderLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customer order orchestration service using order header and order line records.
 *
 * <p>Order placement re-prices the cart, verifies checkout readiness, reserves
 * inventory, creates header/line rows, consumes reservations, and clears the cart.
 * This keeps item-level fulfillment/cancellation/refund extensions possible.</p>
 */
@Service
@RequiredArgsConstructor
public class OrderService {
    private static final String AGGREGATE_TYPE = "Order";

    private final CustomerProfileService customerProfileService;
    private final CartService cartService;
    private final CartPricingService cartPricingService;
    private final InventoryService inventoryService;
    private final CustomerOrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Places an order from the active cart after live price and stock validation.
     */
    @Transactional
    public OrderResponse placeOrder(UUID customerUserId, CreateOrderRequest request) {
        CustomerProfile customer = customerProfileService.requireByUserId(customerUserId);
        Cart cart = cartService.getOrCreateActiveCart(customerUserId);
        CartPricingResult pricing = cartPricingService.price(cart);
        if (pricing.lines().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cannot place order for empty cart");
        }
        pricing.requireCheckoutReady();

        CustomerOrder order = new CustomerOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerProfile(customer);
        order.setStatus(OrderStatus.CREATED);
        order.setSubtotalAmount(pricing.subtotal());
        order.setDiscountAmount(pricing.discountAmount());
        order.setTaxAmount(MoneyUtil.ZERO);
        order.setTotalAmount(pricing.totalAmount());
        order.setShippingAddress(request.shippingAddress());
        CustomerOrder savedOrder = orderRepository.save(order);

        List<OrderLine> lines = new ArrayList<>();
        for (CartPricingResult.CartLinePrice pricedLine : pricing.lines()) {
            List<InventoryReservationAllocation> allocations = inventoryService.reserve(pricedLine.productId(), pricedLine.quantity(), savedOrder.getId());
            lines.addAll(toOrderLines(savedOrder, pricedLine, allocations));
        }
        orderLineRepository.saveAll(lines);
        // Reservation stays active (reservedQuantity > 0) until seller ships or cancels.
        // This allows the seller to see reserved stock in their inventory dashboard.
        savedOrder.setStatus(OrderStatus.PLACED);
        CustomerOrder placedOrder = orderRepository.save(savedOrder);
        cartService.markOrderedAndClear(cart);
        domainEventPublisher.publish(placedOrder.getId(), AGGREGATE_TYPE, DomainEventType.ORDER_CREATED, Map.of(
                "orderId", placedOrder.getId(),
                "orderNumber", placedOrder.getOrderNumber(),
                "customerId", customer.getId()
        ));
        return toResponse(placedOrder, lines);
    }

    /**
     * Returns one customer-owned order or rejects access to another customer order.
     */
    @Transactional(readOnly = true)
    public OrderResponse get(UUID customerUserId, UUID orderId) {
        CustomerProfile customer = customerProfileService.requireByUserId(customerUserId);
        CustomerOrder order = orderRepository.findByIdAndCustomerProfileId(orderId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toResponse(order, orderLineRepository.findByOrderId(order.getId()));
    }

    /**
     * Lists the current customer orders with pagination.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> list(UUID customerUserId, int page, int size) {
        CustomerProfile customer = customerProfileService.requireByUserId(customerUserId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findByCustomerProfileId(customer.getId(), pageable)
                .map(order -> toResponse(order, orderLineRepository.findByOrderId(order.getId())));
    }

    private List<OrderLine> toOrderLines(CustomerOrder order, CartPricingResult.CartLinePrice pricedLine, List<InventoryReservationAllocation> allocations) {
        List<OrderLine> lines = new ArrayList<>();
        BigDecimal allocatedDiscount = MoneyUtil.ZERO;
        for (int index = 0; index < allocations.size(); index++) {
            InventoryReservationAllocation allocation = allocations.get(index);
            BigDecimal lineSubtotal = MoneyUtil.multiply(pricedLine.unitPrice(), allocation.quantity());
            BigDecimal lineDiscount;
            if (index == allocations.size() - 1) {
                lineDiscount = MoneyUtil.money(pricedLine.discountAmount().subtract(allocatedDiscount));
            } else {
                lineDiscount = MoneyUtil.money(pricedLine.discountAmount()
                        .multiply(BigDecimal.valueOf(allocation.quantity()))
                        .divide(BigDecimal.valueOf(pricedLine.quantity()), MoneyUtil.SCALE + 4, MoneyUtil.ROUNDING_MODE));
                allocatedDiscount = MoneyUtil.money(allocatedDiscount.add(lineDiscount));
            }
            OrderLine orderLine = new OrderLine();
            orderLine.setOrder(order);
            orderLine.setProductId(pricedLine.productId());
            orderLine.setProductName(pricedLine.productName());
            orderLine.setWarehouseId(allocation.warehouseId());
            orderLine.setQuantity(allocation.quantity());
            orderLine.setUnitPrice(MoneyUtil.money(pricedLine.unitPrice()));
            orderLine.setSubtotalAmount(lineSubtotal);
            orderLine.setDiscountAmount(lineDiscount);
            orderLine.setTaxAmount(MoneyUtil.ZERO);
            orderLine.setTotalAmount(MoneyUtil.money(lineSubtotal.subtract(lineDiscount)));
            lines.add(orderLine);
        }
        return lines;
    }

    private OrderResponse toResponse(CustomerOrder order, List<OrderLine> lines) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getSubtotalAmount(),
                order.getDiscountAmount(),
                order.getTaxAmount(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getCreatedAt(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private OrderLineResponse toLineResponse(OrderLine line) {
        return new OrderLineResponse(
                line.getId(),
                line.getProductId(),
                line.getProductName(),
                line.getWarehouseId(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getSubtotalAmount(),
                line.getDiscountAmount(),
                line.getTaxAmount(),
                line.getTotalAmount(),
                line.getFulfillmentStatus()
        );
    }

    private String generateOrderNumber() {
        return "ORD-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
