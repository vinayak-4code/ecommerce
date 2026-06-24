package com.acme.ecommerce.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplyCouponRequest(@NotBlank @Size(max = 80) String code) {
}
