package com.acme.ecommerce.coupon.validation;

import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/** Validates reusable coupon rules before a cart receives any discount. */
@Component
public class CouponValidator {
    public void validateDefinition(Coupon coupon) {
        if (coupon.getStartsAt() == null || coupon.getEndsAt() == null) {
            fail("Coupon live and expire dates are required");
        }
        if (!coupon.getEndsAt().isAfter(coupon.getStartsAt())) {
            fail("Coupon expiry must be after live date");
        }
        if (coupon.getDiscountType() == DiscountType.UPTO_PERCENT_OFF) {
            if (coupon.getValue().compareTo(BigDecimal.ZERO) <= 0 || coupon.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                fail("UPTO_PERCENT_OFF value must be between 0 and 100");
            }
            if (coupon.getMaxDiscountAmount() == null || coupon.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
                fail("UPTO_PERCENT_OFF requires a positive maxDiscountAmount cap");
            }
        }
    }

    public void validateUsableNow(Coupon coupon, BigDecimal cartSubtotal) {
        Instant now = Instant.now();
        if (coupon.getStatus() != CouponStatus.ACTIVE) {
            failNotApplicable("Coupon is not active");
        }
        if (coupon.getStartsAt().isAfter(now)) {
            failNotApplicable("Coupon is not live yet");
        }
        if (coupon.getEndsAt().isBefore(now)) {
            failNotApplicable("Coupon has expired");
        }
        if (coupon.getMinCartAmount() != null && cartSubtotal.compareTo(coupon.getMinCartAmount()) < 0) {
            failNotApplicable("Cart subtotal is below coupon minimum amount");
        }
    }

    private void fail(String message) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    private void failNotApplicable(String message) {
        throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, message);
    }
}
