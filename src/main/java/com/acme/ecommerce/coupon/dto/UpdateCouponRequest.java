package com.acme.ecommerce.coupon.dto;

import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Product-admin request for updating the complete coupon configuration. */
public record UpdateCouponRequest(
        @NotBlank @Size(max = 80) String code,
        @Size(max = 500) String description,
        @NotNull DiscountType discountType,
        @NotNull DiscountScope discountScope,
        @NotNull @DecimalMin(value = "0.01") BigDecimal value,
        @DecimalMin(value = "0.00") BigDecimal maxDiscountAmount,
        @DecimalMin(value = "0.00") BigDecimal minCartAmount,
        @NotNull CouponStatus status,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        Set<UUID> categoryIds
) {
}
