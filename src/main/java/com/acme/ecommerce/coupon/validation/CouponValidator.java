package com.acme.ecommerce.coupon.validation;

import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
public class CouponValidator {
    public void validate(Coupon coupon, BigDecimal cartSubtotal, Set<UUID> productIds) {
        Instant now = Instant.now();
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            fail("Coupon is not active");
        }
        if (coupon.getStartsAt() != null && coupon.getStartsAt().isAfter(now)) {
            fail("Coupon is not active yet");
        }
        if (coupon.getEndsAt() != null && coupon.getEndsAt().isBefore(now)) {
            fail("Coupon has expired");
        }
        if (coupon.getMinCartAmount() != null && cartSubtotal.compareTo(coupon.getMinCartAmount()) < 0) {
            fail("Cart subtotal is below coupon minimum amount");
        }
        if (coupon.getProductId() != null && !productIds.contains(coupon.getProductId())) {
            fail("Coupon does not apply to products in cart");
        }
    }

    private void fail(String message) {
        throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, message);
    }
}
