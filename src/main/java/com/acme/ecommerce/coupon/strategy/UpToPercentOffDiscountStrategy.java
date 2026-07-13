package com.acme.ecommerce.coupon.strategy;

import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.entity.Coupon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Calculates percentage discount capped by coupon.maxDiscountAmount. */
@Component
public class UpToPercentOffDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculate(BigDecimal eligibleAmount, Coupon coupon) {
        BigDecimal percentageDiscount = MoneyUtil.percentage(eligibleAmount, coupon.getValue());
        BigDecimal cappedDiscount = coupon.getMaxDiscountAmount() == null || coupon.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) <= 0
                ? percentageDiscount
                : MoneyUtil.min(percentageDiscount, MoneyUtil.money(coupon.getMaxDiscountAmount()));
        return MoneyUtil.min(cappedDiscount, eligibleAmount);
    }
}
