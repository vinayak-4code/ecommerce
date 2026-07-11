package com.acme.ecommerce.coupon.dto;

import com.acme.ecommerce.coupon.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Customer-facing coupon preview used by product detail and cart coupon-picker UI.
 */
public record EligibleCouponResponse(
        UUID couponId,
        String code,
        String description,
        DiscountType discountType,
        BigDecimal value,
        BigDecimal maxDiscountAmount,
        BigDecimal minCartAmount,
        Instant startsAt,
        Instant endsAt,
        String lifecycleStatus,
        BigDecimal estimatedDiscount
) {
}
