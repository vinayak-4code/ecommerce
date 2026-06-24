package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.dto.CartItemResponse;
import com.acme.ecommerce.cart.dto.CartResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartPricingResult(
        UUID cartId,
        List<CartLinePrice> lines,
        String couponCode,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount
) {
    public CartResponse toResponse() {
        return new CartResponse(
                cartId,
                lines.stream().map(CartLinePrice::toResponse).toList(),
                couponCode,
                subtotal,
                discountAmount,
                totalAmount
        );
    }

    public record CartLinePrice(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal totalAmount
    ) {
        public CartItemResponse toResponse() {
            return new CartItemResponse(productId, productName, quantity, unitPrice, subtotal, discountAmount, totalAmount);
        }
    }
}
