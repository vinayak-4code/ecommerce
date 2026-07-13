package com.acme.ecommerce.coupon.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Category restriction for a coupon. A product is eligible if its category or an
 * ancestor category is listed here.
 */
@Getter
@Setter
@Entity
@Table(name = "coupon_category_eligibilities", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_category", columnNames = {"coupon_id", "category_id"})
})
public class CouponCategoryEligibility {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;
}
