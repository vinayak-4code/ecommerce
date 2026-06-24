package com.acme.ecommerce.order.dto;

import com.acme.ecommerce.order.enums.OrderStatus;
import com.acme.ecommerce.order.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String shippingAddress,
        Instant createdAt,
        List<OrderLineResponse> lines
) {
}
