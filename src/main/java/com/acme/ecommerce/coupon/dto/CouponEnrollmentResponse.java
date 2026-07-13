package com.acme.ecommerce.coupon.dto;

import java.time.Instant;
import java.util.UUID;

public record CouponEnrollmentResponse(
        UUID enrollmentId,
        UUID couponId,
        String couponCode,
        UUID productId,
        UUID sellerId,
        Instant createdAt
) {
}
