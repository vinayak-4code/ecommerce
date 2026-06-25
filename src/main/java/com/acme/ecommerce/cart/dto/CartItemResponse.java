package com.acme.ecommerce.cart.dto;

import com.acme.ecommerce.cart.enums.CartItemStockStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItemResponse(
        UUID productId,
        String productName,
        int quantity,
        long availableQuantity,
        CartItemStockStatus stockStatus,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount
) {
}
