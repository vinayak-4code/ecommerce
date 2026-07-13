package com.acme.ecommerce.coupon.repository;

import com.acme.ecommerce.coupon.entity.CouponCategoryEligibility;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CouponCategoryEligibilityRepository extends JpaRepository<CouponCategoryEligibility, UUID> {
    List<CouponCategoryEligibility> findByCouponId(UUID couponId);

    void deleteByCouponId(UUID couponId);
}
