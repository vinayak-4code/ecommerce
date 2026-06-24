package com.acme.ecommerce.coupon.strategy;

import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.entity.Coupon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PercentageDiscountStrategy implements DiscountStrategy {
    @Override
    public BigDecimal calculate(BigDecimal eligibleAmount, Coupon coupon) {
        return MoneyUtil.min(MoneyUtil.percentage(eligibleAmount, coupon.getValue()), eligibleAmount);
    }
}
