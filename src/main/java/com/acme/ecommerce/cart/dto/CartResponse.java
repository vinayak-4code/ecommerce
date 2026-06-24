package com.acme.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID cartId,
        List<CartItemResponse> items,
        String couponCode,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount
) {
}
