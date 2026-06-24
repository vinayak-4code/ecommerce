package com.acme.ecommerce.cart.controller;

import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.cart.dto.ApplyCouponRequest;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.dto.UpdateCartItemQuantityRequest;
import com.acme.ecommerce.cart.service.CartService;
import com.acme.ecommerce.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public CartResponse view() {
        return cartService.view(CurrentUser.require().userId());
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(CurrentUser.require().userId(), request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateQuantity(@PathVariable UUID productId, @Valid @RequestBody UpdateCartItemQuantityRequest request) {
        return cartService.updateQuantity(CurrentUser.require().userId(), productId, request);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable UUID productId) {
        return cartService.removeItem(CurrentUser.require().userId(), productId);
    }

    @PostMapping("/coupon")
    public CartResponse applyCoupon(@Valid @RequestBody ApplyCouponRequest request) {
        return cartService.applyCoupon(CurrentUser.require().userId(), request);
    }

    @DeleteMapping("/coupon")
    public CartResponse removeCoupon() {
        return cartService.removeCoupon(CurrentUser.require().userId());
    }
}
