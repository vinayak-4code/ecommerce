package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.cart.dto.ApplyCouponRequest;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.dto.UpdateCartItemQuantityRequest;
import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.enums.CartStatus;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.cart.repository.CartRepository;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {
    private static final String AGGREGATE_TYPE = "Cart";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerProfileService customerProfileService;
    private final ProductService productService;
    private final CouponService couponService;
    private final CartPricingService cartPricingService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public CartResponse addItem(UUID customerUserId, AddCartItemRequest request) {
        productService.requirePublishedProduct(request.productId());
        Cart cart = getOrCreateActiveCart(customerUserId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.productId())
                .orElseGet(() -> newItem(cart, request.productId()));
        item.setQuantity(item.getQuantity() + request.quantity());
        cartItemRepository.save(item);
        return cartPricingService.price(cart).toResponse();
    }

    @Transactional
    public CartResponse updateQuantity(UUID customerUserId, UUID productId, UpdateCartItemQuantityRequest request) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        if (request.quantity() == 0) {
            cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
            return cartPricingService.price(cart).toResponse();
        }
        productService.requirePublishedProduct(productId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item not found"));
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return cartPricingService.price(cart).toResponse();
    }

    @Transactional
    public CartResponse removeItem(UUID customerUserId, UUID productId) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        return cartPricingService.price(cart).toResponse();
    }

    @Transactional
    public CartResponse view(UUID customerUserId) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        return cartPricingService.price(cart).toResponse();
    }

    @Transactional
    public CartResponse applyCoupon(UUID customerUserId, ApplyCouponRequest request) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        if (cart.getCouponCode() != null && !cart.getCouponCode().isBlank()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Only one coupon can be applied to a cart");
        }
        cart.setCouponCode(couponService.normalize(request.code()));
        Cart saved = cartRepository.save(cart);
        CartResponse response = cartPricingService.price(saved).toResponse();
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, DomainEventType.CART_COUPON_APPLIED, Map.of(
                "cartId", saved.getId(),
                "couponCode", saved.getCouponCode()
        ));
        return response;
    }

    @Transactional
    public CartResponse removeCoupon(UUID customerUserId) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        cart.setCouponCode(null);
        return cartPricingService.price(cartRepository.save(cart)).toResponse();
    }

    @Transactional
    public void markOrderedAndClear(Cart cart) {
        cartItemRepository.deleteByCartId(cart.getId());
        cart.setCouponCode(null);
        cart.setStatus(CartStatus.ORDERED);
        cartRepository.save(cart);
    }

    @Transactional
    public Cart getOrCreateActiveCart(UUID customerUserId) {
        CustomerProfile customer = customerProfileService.requireByUserId(customerUserId);
        return cartRepository.findByCustomerProfileIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> createCart(customer));
    }

    private Cart createCart(CustomerProfile customer) {
        Cart cart = new Cart();
        cart.setCustomerProfile(customer);
        cart.setStatus(CartStatus.ACTIVE);
        return cartRepository.save(cart);
    }

    private CartItem newItem(Cart cart, UUID productId) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProductId(productId);
        item.setQuantity(0);
        return item;
    }
}
