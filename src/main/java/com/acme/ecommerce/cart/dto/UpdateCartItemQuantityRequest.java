package com.acme.ecommerce.cart.dto;

import jakarta.validation.constraints.Min;

public record UpdateCartItemQuantityRequest(
        @Min(0) int quantity
) {
}
