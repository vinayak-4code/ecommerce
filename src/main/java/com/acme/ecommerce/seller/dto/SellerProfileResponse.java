package com.acme.ecommerce.seller.dto;

import java.util.UUID;

public record SellerProfileResponse(
        UUID sellerId,
        String email,
        String businessName,
        String contactNumber
) {
}
