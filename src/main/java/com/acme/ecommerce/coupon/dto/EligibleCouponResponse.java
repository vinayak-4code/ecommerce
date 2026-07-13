package com.acme.ecommerce.coupon.dto;

import com.acme.ecommerce.coupon.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Customer-facing coupon preview used by product detail and cart coupon-picker UI.
 * Includes both fully eligible coupons and "almost eligible" ones (e.g. min cart not met).
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
        BigDecimal estimatedDiscount,
        boolean eligible,
        String reason
) {
    /** Backwards-compatible constructor for fully eligible coupons. */
    public EligibleCouponResponse(UUID couponId, String code, String description, DiscountType discountType,
                                   BigDecimal value, BigDecimal maxDiscountAmount, BigDecimal minCartAmount,
                                   Instant startsAt, Instant endsAt, String lifecycleStatus, BigDecimal estimatedDiscount) {
        this(couponId, code, description, discountType, value, maxDiscountAmount, minCartAmount,
                startsAt, endsAt, lifecycleStatus, estimatedDiscount, true, null);
    }
}
