package com.acme.ecommerce.coupon.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.coupon.dto.CouponEnrollmentResponse;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.dto.UpdateCouponRequest;
import com.acme.ecommerce.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Coupon API. Product Admin creates/updates coupon definitions; sellers enroll
 * products into live coupons; customers apply a single coupon from the cart API.
 */
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {
    private final CouponService couponService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse create(@Valid @RequestBody CreateCouponRequest request) {
        return couponService.create(request);
    }

    @PutMapping("/{couponId}")
    public CouponResponse update(@PathVariable UUID couponId, @Valid @RequestBody UpdateCouponRequest request) {
        return couponService.update(couponId, request);
    }

    @GetMapping
    public Page<CouponResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return couponService.list(page, size);
    }

    @GetMapping("/{code}")
    public CouponResponse get(@PathVariable String code) {
        return couponService.getByCode(code);
    }

    @PostMapping("/{code}/products/{productId}/enroll")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponEnrollmentResponse enroll(@PathVariable String code, @PathVariable UUID productId) {
        return couponService.enrollProduct(CurrentUser.require().userId(), code, productId);
    }

    @DeleteMapping("/{code}/products/{productId}/enroll")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unenroll(@PathVariable String code, @PathVariable UUID productId) {
        couponService.unenrollProduct(CurrentUser.require().userId(), code, productId);
    }
}
