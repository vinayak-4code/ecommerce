package com.acme.ecommerce.coupon.controller;

import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{code}")
    public CouponResponse get(@PathVariable String code) {
        return couponService.getByCode(code);
    }
}
