package com.acme.ecommerce.order.dto;

import com.acme.ecommerce.order.enums.FulfillmentStatus;
import com.acme.ecommerce.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Seller-facing order-line view.
 *
 * <p>A seller only sees lines containing products they own. Customer-wide order details that are not
 * needed for fulfillment are intentionally omitted.</p>
 */
public record SellerOrderLineResponse(
        UUID orderId,
        String orderNumber,
        OrderStatus orderStatus,
        Instant createdAt,
        String customerName,
        String shippingAddress,
        UUID lineId,
        UUID productId,
        String productName,
        UUID warehouseId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        FulfillmentStatus fulfillmentStatus
) {
}
