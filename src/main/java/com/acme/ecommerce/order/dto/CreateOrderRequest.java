package com.acme.ecommerce.order.dto;

import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @Size(max = 1000) String shippingAddress
) {
}
