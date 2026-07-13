package com.acme.ecommerce.coupon.dto;

import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Product-admin request for creating a coupon.
 * For UPTO_PERCENT_OFF, value is the percentage and maxDiscountAmount is the required cap.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateCouponRequest(
        @NotBlank @Size(max = 80) String code,
        @Size(max = 500) String description,
        @NotNull DiscountType discountType,
        @NotNull DiscountScope discountScope,
        @NotNull @DecimalMin(value = "0.01") BigDecimal value,
        @DecimalMin(value = "0.00") BigDecimal maxDiscountAmount,
        @DecimalMin(value = "0.00") BigDecimal minCartAmount,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        Set<UUID> categoryIds
) {
}
