package com.acme.ecommerce.coupon.validation;

import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit specification for coupon definition and runtime applicability rules. */
class CouponValidatorTest {

    private final CouponValidator validator = new CouponValidator();

    @Test
    void validateDefinition_shouldPass_whenFlatCouponHasValidWindow() {
        // Given
        Coupon coupon = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));

        // When
        validator.validateDefinition(coupon);

        // Then
        // No exception means the coupon definition can be persisted.
    }

    @Test
    void validateDefinition_shouldRejectMissingOrInvalidDateWindow() {
        // Given
        Coupon missingDates = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, null, null);
        Coupon reversedDates = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, Instant.now().plusSeconds(3600), Instant.now());

        // When / Then
        assertThatThrownBy(() -> validator.validateDefinition(missingDates))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("live and expire dates are required");
        assertThatThrownBy(() -> validator.validateDefinition(reversedDates))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expiry must be after live date");
    }

    @Test
    void validateDefinition_shouldRejectPercentCoupon_whenValueOrCapIsInvalid() {
        // Given
        Coupon percentTooHigh = coupon(DiscountType.UPTO_PERCENT_OFF, new BigDecimal("101.00"), new BigDecimal("500.00"), Instant.now(), Instant.now().plusSeconds(3600));
        Coupon missingCap = coupon(DiscountType.UPTO_PERCENT_OFF, new BigDecimal("10.00"), null, Instant.now(), Instant.now().plusSeconds(3600));

        // When / Then
        assertThatThrownBy(() -> validator.validateDefinition(percentTooHigh))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("between 0 and 100");
        assertThatThrownBy(() -> validator.validateDefinition(missingCap))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires a positive maxDiscountAmount cap");
    }

    @Test
    void validateUsableNow_shouldPass_whenCouponIsActiveLiveAndMinimumIsMet() {
        // Given
        Coupon coupon = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        coupon.setMinCartAmount(new BigDecimal("100.00"));

        // When
        validator.validateUsableNow(coupon, new BigDecimal("150.00"));

        // Then
        // No exception means cart pricing can apply the coupon.
    }

    @Test
    void validateUsableNow_shouldRejectInactiveScheduledExpiredOrBelowMinimumCoupon() {
        // Given
        Coupon inactive = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        inactive.setStatus(CouponStatus.INACTIVE);
        Coupon scheduled = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, Instant.now().plusSeconds(60), Instant.now().plusSeconds(3600));
        Coupon expired = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60));
        Coupon belowMinimum = coupon(DiscountType.FLAT, new BigDecimal("50.00"), null, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        belowMinimum.setMinCartAmount(new BigDecimal("200.00"));

        // When / Then
        assertThatThrownBy(() -> validator.validateUsableNow(inactive, new BigDecimal("250.00"))).isInstanceOf(BusinessException.class).hasMessageContaining("not active");
        assertThatThrownBy(() -> validator.validateUsableNow(scheduled, new BigDecimal("250.00"))).isInstanceOf(BusinessException.class).hasMessageContaining("not live yet");
        assertThatThrownBy(() -> validator.validateUsableNow(expired, new BigDecimal("250.00"))).isInstanceOf(BusinessException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> validator.validateUsableNow(belowMinimum, new BigDecimal("100.00"))).isInstanceOf(BusinessException.class).hasMessageContaining("below coupon minimum amount");
    }

    private Coupon coupon(DiscountType type, BigDecimal value, BigDecimal maxDiscountAmount, Instant startsAt, Instant endsAt) {
        Coupon coupon = new Coupon();
        coupon.setCode("SAVE");
        coupon.setDescription("Test coupon");
        coupon.setDiscountType(type);
        coupon.setDiscountScope(DiscountScope.CART);
        coupon.setValue(value);
        coupon.setMaxDiscountAmount(maxDiscountAmount);
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStartsAt(startsAt);
        coupon.setEndsAt(endsAt);
        return coupon;
    }
}
