package com.acme.ecommerce.common.security;

import com.acme.ecommerce.auth.enums.UserRole;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email, UserRole role) {
    public boolean isProductAdmin() {
        return role == UserRole.PRODUCT_ADMIN;
    }

    public boolean isSeller() {
        return role == UserRole.SELLER;
    }

    public boolean isCustomer() {
        return role == UserRole.CUSTOMER;
    }
}
