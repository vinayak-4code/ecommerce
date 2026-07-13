package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.dto.CartItemResponse;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Immutable pricing result returned by {@link CartPricingService}.
 *
 * <p>It keeps calculated line-level values separate from persisted cart data,
 * making order placement and API responses use the same calculation output.</p>
 */
public record CartPricingResult(
        UUID cartId,
        List<CartLinePrice> lines,
        String couponCode,
        boolean checkoutReady,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount
) {
    /**
     * Converts the internal pricing result into the public cart response DTO.
     */
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

    /**
     * Fails checkout when any cart line is out of stock or under-stocked.
     */
    public void requireCheckoutReady() {
        if (!checkoutReady) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Cart contains out-of-stock or insufficient-stock items");
        }
    }

    /**
     * Calculated price and stock result for one cart line.
     */
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
        /**
         * Converts this line into the public cart line response.
         */
        public CartItemResponse toResponse() {
            return new CartItemResponse(productId, productName, quantity, availableQuantity, stockStatus, unitPrice, subtotal, discountAmount, totalAmount);
        }
    }
}
