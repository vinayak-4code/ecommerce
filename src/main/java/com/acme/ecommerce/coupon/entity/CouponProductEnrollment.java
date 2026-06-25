package com.acme.ecommerce.coupon.entity;

import com.acme.ecommerce.seller.entity.SellerProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Seller opt-in record connecting a seller-owned product to a product-admin coupon.
 * This allows the same product to participate in multiple coupons while the cart
 * still applies only one coupon code at a time.
 */
@Getter
@Setter
@Entity
@Table(name = "coupon_product_enrollments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_product_enrollment", columnNames = {"coupon_id", "product_id"})
})
public class CouponProductEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerProfile sellerProfile;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
