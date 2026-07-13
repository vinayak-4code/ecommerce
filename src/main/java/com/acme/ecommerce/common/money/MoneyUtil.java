package com.acme.ecommerce.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING_MODE);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100).setScale(SCALE, ROUNDING_MODE);

    private MoneyUtil() {
    }

    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return ZERO;
        }
        return value.setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal multiply(BigDecimal amount, int quantity) {
        return money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    public static BigDecimal percentage(BigDecimal amount, BigDecimal percentage) {
        return money(amount.multiply(percentage).divide(HUNDRED, SCALE + 4, ROUNDING_MODE));
    }

    public static BigDecimal min(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
