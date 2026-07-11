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
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.coupon.dto.EligibleCouponResponse;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.service.CustomerProfileService;
import com.acme.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * Customer cart command service.
 *
 * <p>The cart keeps a lightweight product/quantity model, while view/order flows
 * revalidate live price and inventory. Quantity zero removes an item, so removing
 * the last item leaves a valid empty cart.</p>
 */
@Service
@RequiredArgsConstructor
public class CartService {
    private static final String AGGREGATE_TYPE = "Cart";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerProfileService customerProfileService;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CouponService couponService;
    private final CartPricingService cartPricingService;
    private final InventoryService inventoryService;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Adds or increments a cart item after checking the product is published and stock exists.
     */
    @Transactional
    public CartResponse addItem(UUID customerUserId, AddCartItemRequest request) {
        productService.requirePublishedProduct(request.productId());
        Cart cart = getOrCreateActiveCart(customerUserId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), request.productId())
                .orElseGet(() -> newItem(cart, request.productId()));
        int newQuantity = item.getQuantity() + request.quantity();
        ensureAvailable(request.productId(), newQuantity);
        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
        return cartPricingService.price(cart).toResponse();
    }

    /**
     * Changes requested quantity; quantity zero is interpreted as remove item.
     */
    @Transactional
    public CartResponse updateQuantity(UUID customerUserId, UUID productId, UpdateCartItemQuantityRequest request) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        if (request.quantity() == 0) {
            cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
            return cartPricingService.price(cart).toResponse();
        }
        productService.requirePublishedProduct(productId);
        ensureAvailable(productId, request.quantity());
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Cart item not found"));
        item.setQuantity(request.quantity());
        cartItemRepository.save(item);
        return cartPricingService.price(cart).toResponse();
    }

    /**
     * Removes one product line from the active customer cart.
     */
    @Transactional
    public CartResponse removeItem(UUID customerUserId, UUID productId) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        return cartPricingService.price(cart).toResponse();
    }

    /**
     * Returns a freshly priced cart using current product price, coupon, and inventory data.
     */
    @Transactional
    public CartResponse view(UUID customerUserId) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        return cartPricingService.price(cart).toResponse();
    }


    /**
     * Returns coupons currently applicable to this customer cart with estimated savings.
     */
    @Transactional
    public List<EligibleCouponResponse> eligibleCoupons(UUID customerUserId) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            return List.of();
        }
        Set<UUID> productIds = items.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> products = productRepository.findByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<UUID, BigDecimal> subtotalByProductId = items.stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .collect(Collectors.toMap(
                        CartItem::getProductId,
                        item -> com.acme.ecommerce.common.money.MoneyUtil.multiply(products.get(item.getProductId()).getPrice(), item.getQuantity()),
                        BigDecimal::add
                ));
        BigDecimal subtotal = subtotalByProductId.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return couponService.eligibleForCart(products, subtotalByProductId, subtotal);
    }

    /**
     * Applies a normalized coupon code while enforcing the one-coupon-per-cart rule.
     */
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

    /**
     * Clears the applied coupon and recalculates totals.
     */
    @Transactional
    public CartResponse removeCoupon(UUID customerUserId) {
        Cart cart = getOrCreateActiveCart(customerUserId);
        cart.setCouponCode(null);
        return cartPricingService.price(cartRepository.save(cart)).toResponse();
    }

    /**
     * Marks the cart as ordered and removes lines after successful order creation.
     */
    @Transactional
    public void markOrderedAndClear(Cart cart) {
        cartItemRepository.deleteByCartId(cart.getId());
        cart.setCouponCode(null);
        cart.setStatus(CartStatus.ORDERED);
        cartRepository.save(cart);
    }

    /**
     * Finds the active cart for the customer or creates one lazily.
     */
    @Transactional
    public Cart getOrCreateActiveCart(UUID customerUserId) {
        CustomerProfile customer = customerProfileService.requireByUserId(customerUserId);
        return cartRepository.findByCustomerProfileIdAndStatus(customer.getId(), CartStatus.ACTIVE)
                .orElseGet(() -> createCart(customer));
    }

    private void ensureAvailable(UUID productId, int requestedQuantity) {
        long available = inventoryService.consolidatedAvailable(productId);
        if (available <= 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Product is out of stock");
        }
        if (requestedQuantity > available) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_INVENTORY, "Requested quantity exceeds available inventory");
        }
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
