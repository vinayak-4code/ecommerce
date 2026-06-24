package com.acme.ecommerce.cart.entity;

import com.acme.ecommerce.cart.enums.CartStatus;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "carts", indexes = {
        @Index(name = "idx_cart_customer_status", columnList = "customer_id, status")
})
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerProfile customerProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CartStatus status = CartStatus.ACTIVE;

    @Column(name = "coupon_code", length = 80)
    private String couponCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
