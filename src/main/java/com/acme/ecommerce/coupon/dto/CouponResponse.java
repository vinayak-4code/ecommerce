package com.acme.ecommerce.coupon.dto;

import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CouponResponse(
        UUID id,
        String code,
        String description,
        DiscountType discountType,
        DiscountScope discountScope,
        BigDecimal value,
        BigDecimal minCartAmount,
        UUID productId,
        CouponStatus status,
        Instant startsAt,
        Instant endsAt
) {
}
