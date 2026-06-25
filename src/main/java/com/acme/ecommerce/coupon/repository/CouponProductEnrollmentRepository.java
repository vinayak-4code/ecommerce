package com.acme.ecommerce.coupon.repository;

import com.acme.ecommerce.coupon.entity.CouponProductEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CouponProductEnrollmentRepository extends JpaRepository<CouponProductEnrollment, UUID> {
    Optional<CouponProductEnrollment> findByCouponIdAndProductId(UUID couponId, UUID productId);

    boolean existsByCouponIdAndProductId(UUID couponId, UUID productId);

    void deleteByCouponIdAndProductId(UUID couponId, UUID productId);

    List<CouponProductEnrollment> findByCouponId(UUID couponId);

    @Query("select e.productId from CouponProductEnrollment e where e.coupon.id = :couponId and e.productId in :productIds")
    Set<UUID> findEnrolledProductIds(@Param("couponId") UUID couponId, @Param("productIds") Set<UUID> productIds);
}
