package com.acme.ecommerce.coupon.strategy;

import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.DiscountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit specification for reusable discount strategies and factory selection. */
class DiscountStrategyTest {

    private final FlatDiscountStrategy flatDiscountStrategy = new FlatDiscountStrategy();
    private final UpToPercentOffDiscountStrategy percentStrategy = new UpToPercentOffDiscountStrategy();
    private final DiscountStrategyFactory factory = new DiscountStrategyFactory(percentStrategy, flatDiscountStrategy);

    @Test
    void flatDiscount_shouldClampDiscountToEligibleAmount_whenCouponValueIsGreaterThanSubtotal() {
        // Given
        Coupon coupon = coupon(DiscountType.FLAT, new BigDecimal("150.00"), null);

        // When
        BigDecimal discount = flatDiscountStrategy.calculate(new BigDecimal("100.00"), coupon);

        // Then
        assertThat(discount).isEqualByComparingTo("100.00");
    }

    @Test
    void upToPercentOff_shouldCalculatePercentageAndRespectMaxDiscountCap() {
        // Given
        Coupon coupon = coupon(DiscountType.UPTO_PERCENT_OFF, new BigDecimal("20.00"), new BigDecimal("50.00"));

        // When
        BigDecimal discount = percentStrategy.calculate(new BigDecimal("1000.00"), coupon);

        // Then
        assertThat(discount).isEqualByComparingTo("50.00");
    }

    @Test
    void upToPercentOff_shouldClampDiscountToEligibleAmount_whenCalculatedDiscountExceedsEligibleAmount() {
        // Given
        Coupon coupon = coupon(DiscountType.UPTO_PERCENT_OFF, new BigDecimal("90.00"), new BigDecimal("1000.00"));

        // When
        BigDecimal discount = percentStrategy.calculate(new BigDecimal("10.00"), coupon);

        // Then
        assertThat(discount).isEqualByComparingTo("9.00");
    }

    @Test
    void factory_shouldReturnStrategyMatchingDiscountType() {
        // Given / When / Then
        assertThat(factory.get(DiscountType.FLAT)).isSameAs(flatDiscountStrategy);
        assertThat(factory.get(DiscountType.UPTO_PERCENT_OFF)).isSameAs(percentStrategy);
    }

    private Coupon coupon(DiscountType type, BigDecimal value, BigDecimal maxDiscountAmount) {
        Coupon coupon = new Coupon();
        coupon.setDiscountType(type);
        coupon.setValue(value);
        coupon.setMaxDiscountAmount(maxDiscountAmount);
        return coupon;
    }
}
