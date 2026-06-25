package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.dto.CartItemResponse;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CartPricingResult(
        UUID cartId,
        List<CartLinePrice> lines,
        String couponCode,
        boolean checkoutReady,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount
) {
    public CartResponse toResponse() {
        return new CartResponse(
                cartId,
                lines.stream().map(CartLinePrice::toResponse).toList(),
                couponCode,
                checkoutReady,
                subtotal,
                discountAmount,
                totalAmount
        );
    }

    public void requireCheckoutReady() {
        if (!checkoutReady) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Cart contains out-of-stock or insufficient-stock items");
        }
    }

    public record CartLinePrice(
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
        public CartItemResponse toResponse() {
            return new CartItemResponse(productId, productName, quantity, availableQuantity, stockStatus, unitPrice, subtotal, discountAmount, totalAmount);
        }
    }
}
