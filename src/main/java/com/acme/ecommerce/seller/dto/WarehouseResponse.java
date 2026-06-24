package com.acme.ecommerce.seller.dto;

import com.acme.ecommerce.seller.enums.WarehouseStatus;

import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String name,
        String code,
        String addressLine1,
        String city,
        String state,
        String country,
        String postalCode,
        WarehouseStatus status
) {
}
