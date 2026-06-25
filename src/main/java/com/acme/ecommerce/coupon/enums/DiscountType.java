package com.acme.ecommerce.coupon.enums;

/**
 * Supported concise discount models.
 * FLAT subtracts a fixed amount; UPTO_PERCENT_OFF applies a percentage capped by maxDiscountAmount.
 */
public enum DiscountType {
    FLAT,
    UPTO_PERCENT_OFF
}
