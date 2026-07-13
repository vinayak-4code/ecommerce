package com.acme.ecommerce.common.event;

/** Stable domain event names persisted to outbox and consumed by projections. */
public enum DomainEventType {
    PRODUCT_CREATED,
    PRODUCT_UPDATED,
    PRODUCT_DELETED,
    PRODUCT_PUBLISHED,
    PRODUCT_UNPUBLISHED,
    INVENTORY_ADDED,
    INVENTORY_ADJUSTED,
    INVENTORY_RESERVED,
    INVENTORY_RELEASED,
    INVENTORY_CONSUMED,
    COUPON_CREATED,
    COUPON_UPDATED,
    CART_COUPON_APPLIED,
    ORDER_CREATED,
    ORDER_CANCELLED
}
