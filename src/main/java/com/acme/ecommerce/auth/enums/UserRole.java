package com.acme.ecommerce.auth.enums;

/**
 * Platform roles used by Spring Security and business authorization checks.
 * PRODUCT_ADMIN owns catalog governance and coupon configuration, SELLER owns
 * products/inventory, and CUSTOMER owns cart/order operations.
 */
public enum UserRole {
    PRODUCT_ADMIN,
    SELLER,
    CUSTOMER
}
