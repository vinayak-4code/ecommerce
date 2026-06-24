package com.acme.ecommerce.cart.repository;

import com.acme.ecommerce.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findByCartId(UUID cartId);

    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);
    void deleteByCartIdAndProductId(UUID cartId, UUID productId);
    void deleteByCartId(UUID cartId);
}
