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

/**
 * Customer cart API. The cart stores product ids and quantities, while each view
 * reloads live product price, coupon rules, and warehouse inventory.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    /**
     * Returns the active customer cart after reloading current product price, stock, and coupon eligibility.
     */
    @GetMapping
    public CartResponse view() {
        return cartService.view(CurrentUser.require().userId());
    }

    /**
     * Adds a product to the current customer cart or increments quantity if it already exists.
     */
    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(CurrentUser.require().userId(), request);
    }

    /**
     * Updates quantity for one cart line; quantity zero removes the line from the cart.
     */
    @PutMapping("/items/{productId}")
    public CartResponse updateQuantity(@PathVariable UUID productId, @Valid @RequestBody UpdateCartItemQuantityRequest request) {
        return cartService.updateQuantity(CurrentUser.require().userId(), productId, request);
    }

    /**
     * Removes a product from the cart and returns the recalculated cart response.
     */
    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable UUID productId) {
        return cartService.removeItem(CurrentUser.require().userId(), productId);
    }

    /**
     * Applies one coupon code to the cart. Existing coupon, if any, is replaced.
     */
    @PostMapping({"/coupon", "/coupons"})
    public CartResponse applyCoupon(@Valid @RequestBody ApplyCouponRequest request) {
        return cartService.applyCoupon(CurrentUser.require().userId(), request);
    }

    /**
     * Removes the applied coupon and returns totals without discount.
     */
    @DeleteMapping({"/coupon", "/coupons"})
    public CartResponse removeCoupon() {
        return cartService.removeCoupon(CurrentUser.require().userId());
    }
}
