package com.acme.ecommerce.coupon.strategy;

import com.acme.ecommerce.coupon.enums.DiscountType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscountStrategyFactory {
    private final UpToPercentOffDiscountStrategy upToPercentOffDiscountStrategy;
    private final FlatDiscountStrategy flatDiscountStrategy;

    public DiscountStrategy get(DiscountType type) {
        return switch (type) {
            case UPTO_PERCENT_OFF -> upToPercentOffDiscountStrategy;
            case FLAT -> flatDiscountStrategy;
        };
    }
}
