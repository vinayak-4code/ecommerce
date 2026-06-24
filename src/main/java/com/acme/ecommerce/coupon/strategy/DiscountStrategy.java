package com.acme.ecommerce.coupon.strategy;

import com.acme.ecommerce.coupon.entity.Coupon;

import java.math.BigDecimal;

public interface DiscountStrategy {
    BigDecimal calculate(BigDecimal eligibleAmount, Coupon coupon);
}
