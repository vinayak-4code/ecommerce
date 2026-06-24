package com.acme.ecommerce.order.dto;

import com.acme.ecommerce.order.enums.FulfillmentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineResponse(
        UUID id,
        UUID productId,
        String productName,
        UUID warehouseId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        FulfillmentStatus fulfillmentStatus
) {
}
