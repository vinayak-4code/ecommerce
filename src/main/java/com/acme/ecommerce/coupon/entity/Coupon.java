package com.acme.ecommerce.coupon.entity;

import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Product-admin configured coupon definition.
 * Example: ELECTRO10 uses UPTO_PERCENT_OFF, value=10, maxDiscountAmount=500,
 * and category eligibility limited to Electronics descendants.
 */
@Getter
@Setter
@Entity
@Table(name = "coupons", uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_code", columnNames = "code")
})
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 40)
    private DiscountType discountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_scope", nullable = false, length = 40)
    private DiscountScope discountScope;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value;

    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_cart_amount", precision = 19, scale = 2)
    private BigDecimal minCartAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CouponStatus status = CouponStatus.ACTIVE;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
