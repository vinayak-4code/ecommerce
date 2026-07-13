package com.acme.ecommerce.catalog.enums;

/**
 * Supported metadata types for product attributes.
 * The seller product form uses this enum to render the right input control.
 */
public enum AttributeType {
    STRING,
    NUMBER,
    DECIMAL,
    BOOLEAN,
    SELECT,
    MULTI_SELECT
}
