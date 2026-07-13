package com.acme.ecommerce.cart.repository;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByCustomerProfileIdAndStatus(UUID customerId, CartStatus status);
}
