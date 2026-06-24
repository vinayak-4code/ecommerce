package com.acme.ecommerce.coupon.strategy;

import com.acme.ecommerce.coupon.enums.DiscountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscountStrategyFactory {
    private final PercentageDiscountStrategy percentageDiscountStrategy;
    private final FlatDiscountStrategy flatDiscountStrategy;

    public DiscountStrategy get(DiscountType type) {
        return switch (type) {
            case PERCENTAGE -> percentageDiscountStrategy;
            case FLAT -> flatDiscountStrategy;
        };
    }
}
