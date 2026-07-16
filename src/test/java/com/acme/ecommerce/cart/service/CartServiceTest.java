package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.cart.dto.ApplyCouponRequest;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.dto.UpdateCartItemQuantityRequest;
import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.cart.enums.CartStatus;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.cart.repository.CartRepository;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.coupon.dto.EligibleCouponResponse;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.service.CustomerProfileService;
import com.acme.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit specification for customer cart commands.
 *
 * <p>The service keeps persisted cart data lightweight and delegates live pricing
 * to {@link CartPricingService}. These tests therefore mock product validation,
 * inventory checks, coupon validation, and pricing while asserting the cart state
 * changes and orchestration rules.</p>
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private CustomerProfileService customerProfileService;

    @Mock
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private CartPricingService cartPricingService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private CartService cartService;

    @Test
    void addItem_shouldCreateNewCartItem_whenProductIsPublishedAndInventoryIsAvailable() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        AddCartItemRequest request = new AddCartItemRequest(productId, 2);
        CartPricingResult pricedCart = pricedCart(cartId, null, BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.valueOf(50));

        givenActiveCart(customerUserId, customer, cart);
        when(productService.requirePublishedProduct(productId)).thenReturn(product(productId, BigDecimal.valueOf(25)));
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Optional.empty());
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(5L);
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.addItem(customerUserId, request);

        // Then
        ArgumentCaptor<CartItem> savedItem = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(savedItem.capture());

        assertThat(savedItem.getValue().getCart()).isSameAs(cart);
        assertThat(savedItem.getValue().getProductId()).isEqualTo(productId);
        assertThat(savedItem.getValue().getQuantity()).isEqualTo(2);
        assertThat(response.cartId()).isEqualTo(cartId);
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");

        verify(productService).requirePublishedProduct(productId);
        verify(inventoryService).consolidatedAvailable(productId);
        verify(cartPricingService).price(cart);
    }

    @Test
    void addItem_shouldIncrementExistingCartItem_whenProductAlreadyExistsInCart() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        CartItem existingItem = cartItem(cart, productId, 2);
        AddCartItemRequest request = new AddCartItemRequest(productId, 3);
        CartPricingResult pricedCart = pricedCart(cartId, null, BigDecimal.valueOf(125), BigDecimal.ZERO, BigDecimal.valueOf(125));

        givenActiveCart(customerUserId, customer, cart);
        when(productService.requirePublishedProduct(productId)).thenReturn(product(productId, BigDecimal.valueOf(25)));
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Optional.of(existingItem));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(5L);
        when(cartItemRepository.save(existingItem)).thenReturn(existingItem);
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.addItem(customerUserId, request);

        // Then
        assertThat(existingItem.getQuantity()).isEqualTo(5);
        assertThat(response.subtotal()).isEqualByComparingTo("125.00");
        verify(cartItemRepository).save(existingItem);
        verify(inventoryService).consolidatedAvailable(productId);
    }

    @Test
    void addItem_shouldThrowBusinessException_whenRequestedQuantityExceedsAvailableInventory() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        AddCartItemRequest request = new AddCartItemRequest(productId, 10);

        givenActiveCart(customerUserId, customer, cart);
        when(productService.requirePublishedProduct(productId)).thenReturn(product(productId, BigDecimal.TEN));
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Optional.empty());
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(3L);

        // When / Then
        assertThatThrownBy(() -> cartService.addItem(customerUserId, request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_INVENTORY);
                    assertThat(exception).hasMessageContaining("Requested quantity exceeds available inventory");
                });

        verify(cartItemRepository, never()).save(any(CartItem.class));
        verifyNoInteractions(cartPricingService);
    }

    @Test
    void updateQuantity_shouldDeleteCartItemAndReprice_whenRequestedQuantityIsZero() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        CartPricingResult pricedCart = pricedCart(cartId, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        givenActiveCart(customerUserId, customer, cart);
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.updateQuantity(customerUserId, productId, new UpdateCartItemQuantityRequest(0));

        // Then
        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmount()).isEqualByComparingTo("0.00");

        verify(cartItemRepository).deleteByCartIdAndProductId(cartId, productId);
        verifyNoInteractions(productService, inventoryService);
        verify(cartPricingService).price(cart);
    }

    @Test
    void updateQuantity_shouldUpdateExistingItem_whenQuantityIsPositiveAndInventoryIsAvailable() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        CartItem existingItem = cartItem(cart, productId, 1);
        CartPricingResult pricedCart = pricedCart(cartId, null, BigDecimal.valueOf(75), BigDecimal.ZERO, BigDecimal.valueOf(75));

        givenActiveCart(customerUserId, customer, cart);
        when(productService.requirePublishedProduct(productId)).thenReturn(product(productId, BigDecimal.valueOf(25)));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(10L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(existingItem)).thenReturn(existingItem);
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.updateQuantity(customerUserId, productId, new UpdateCartItemQuantityRequest(3));

        // Then
        assertThat(existingItem.getQuantity()).isEqualTo(3);
        assertThat(response.subtotal()).isEqualByComparingTo("75.00");
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    void updateQuantity_shouldThrowBusinessException_whenCartItemDoesNotExist() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);

        givenActiveCart(customerUserId, customer, cart);
        when(productService.requirePublishedProduct(productId)).thenReturn(product(productId, BigDecimal.TEN));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(5L);
        when(cartItemRepository.findByCartIdAndProductId(cartId, productId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> cartService.updateQuantity(customerUserId, productId, new UpdateCartItemQuantityRequest(2)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                    assertThat(exception).hasMessageContaining("Cart item not found");
                });

        verify(cartItemRepository, never()).save(any(CartItem.class));
        verifyNoInteractions(cartPricingService);
    }

    @Test
    void removeItem_shouldDeleteProductLineAndReturnRepricedCart() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        CartPricingResult pricedCart = pricedCart(cartId, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        givenActiveCart(customerUserId, customer, cart);
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.removeItem(customerUserId, productId);

        // Then
        assertThat(response.cartId()).isEqualTo(cartId);
        assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
        verify(cartItemRepository).deleteByCartIdAndProductId(cartId, productId);
        verify(cartPricingService).price(cart);
    }

    @Test
    void view_shouldReturnFreshlyPricedActiveCart() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        CartPricingResult pricedCart = pricedCart(cartId, "SAVE10", BigDecimal.valueOf(100), BigDecimal.TEN, BigDecimal.valueOf(90));

        givenActiveCart(customerUserId, customer, cart);
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.view(customerUserId);

        // Then
        assertThat(response.cartId()).isEqualTo(cartId);
        assertThat(response.couponCode()).isEqualTo("SAVE10");
        assertThat(response.totalAmount()).isEqualByComparingTo("90.00");
        verify(cartPricingService).price(cart);
    }

    @Test
    void eligibleCoupons_shouldReturnEmptyList_whenCartHasNoItems() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);

        givenActiveCart(customerUserId, customer, cart);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        // When
        List<EligibleCouponResponse> responses = cartService.eligibleCoupons(customerUserId);

        // Then
        assertThat(responses).isEmpty();
        verifyNoInteractions(productRepository, couponService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void eligibleCoupons_shouldBuildProductSubtotalsAndDelegateToCouponService_whenCartHasItems() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        CartItem item1 = cartItem(cart, productId1, 1);
        CartItem item2 = cartItem(cart, productId2, 2);
        Product product1 = product(productId1, categoryId, BigDecimal.valueOf(100));
        Product product2 = product(productId2, categoryId, BigDecimal.valueOf(50));
        EligibleCouponResponse couponResponse = eligibleCoupon("SAVE20", BigDecimal.valueOf(20));

        givenActiveCart(customerUserId, customer, cart);
        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item1, item2));
        when(productRepository.findByIdIn(Set.of(productId1, productId2))).thenReturn(List.of(product1, product2));
        when(couponService.eligibleForCart(anyMap(), anyMap(), eq(new BigDecimal("200.00"))))
                .thenReturn(List.of(couponResponse));

        // When
        List<EligibleCouponResponse> responses = cartService.eligibleCoupons(customerUserId);

        // Then
        assertThat(responses).containsExactly(couponResponse);

        ArgumentCaptor<Map> productsCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> subtotalsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(couponService).eligibleForCart(productsCaptor.capture(), subtotalsCaptor.capture(), eq(new BigDecimal("200.00")));

        Map<UUID, Product> productsById = productsCaptor.getValue();
        Map<UUID, BigDecimal> subtotalByProductId = subtotalsCaptor.getValue();
        assertThat(productsById).containsEntry(productId1, product1).containsEntry(productId2, product2);
        assertThat(subtotalByProductId.get(productId1)).isEqualByComparingTo("100.00");
        assertThat(subtotalByProductId.get(productId2)).isEqualByComparingTo("100.00");
    }

    @Test
    void applyCoupon_shouldNormalizeValidateSavePublishEventAndReturnRepricedCart() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();
        String rawCode = " save10 ";
        String normalizedCode = "SAVE10";

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        CartPricingResult pricedCart = pricedCart(cartId, normalizedCode, BigDecimal.valueOf(100), BigDecimal.TEN, BigDecimal.valueOf(90));

        givenActiveCart(customerUserId, customer, cart);
        when(couponService.normalize(rawCode)).thenReturn(normalizedCode);
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.applyCoupon(customerUserId, new ApplyCouponRequest(rawCode));

        // Then
        assertThat(cart.getCouponCode()).isEqualTo(normalizedCode);
        assertThat(response.couponCode()).isEqualTo(normalizedCode);
        assertThat(response.discountAmount()).isEqualByComparingTo("10.00");

        verify(couponService).validateApplicableToCart(normalizedCode, cartId);
        verify(cartRepository).save(cart);
        verify(domainEventPublisher).publish(eq(cartId), eq("Cart"), eq(DomainEventType.CART_COUPON_APPLIED), anyMap());
    }

    @Test
    void applyCoupon_shouldRejectSecondCoupon_whenCartAlreadyHasCouponCode() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        cart.setCouponCode("SAVE10");

        givenActiveCart(customerUserId, customer, cart);

        // When / Then
        assertThatThrownBy(() -> cartService.applyCoupon(customerUserId, new ApplyCouponRequest("SAVE20")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);
                    assertThat(exception).hasMessageContaining("Only one coupon can be applied");
                });

        verifyNoInteractions(couponService, cartPricingService, domainEventPublisher);
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void removeCoupon_shouldClearCouponAndReturnRepricedCart() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        UUID cartId = UUID.randomUUID();

        CustomerProfile customer = customerProfile(UUID.randomUUID());
        Cart cart = activeCart(cartId, customer);
        cart.setCouponCode("SAVE10");
        CartPricingResult pricedCart = pricedCart(cartId, null, BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(100));

        givenActiveCart(customerUserId, customer, cart);
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartPricingService.price(cart)).thenReturn(pricedCart);

        // When
        CartResponse response = cartService.removeCoupon(customerUserId);

        // Then
        assertThat(cart.getCouponCode()).isNull();
        assertThat(response.couponCode()).isNull();
        assertThat(response.discountAmount()).isEqualByComparingTo("0.00");
        verify(cartRepository).save(cart);
    }

    @Test
    void markOrderedAndClear_shouldDeleteLinesClearCouponAndMarkCartOrdered() {
        // Given
        UUID cartId = UUID.randomUUID();
        Cart cart = activeCart(cartId, customerProfile(UUID.randomUUID()));
        cart.setCouponCode("SAVE10");

        when(cartRepository.save(cart)).thenReturn(cart);

        // When
        cartService.markOrderedAndClear(cart);

        // Then
        assertThat(cart.getCouponCode()).isNull();
        assertThat(cart.getStatus()).isEqualTo(CartStatus.ORDERED);
        verify(cartItemRepository).deleteByCartId(cartId);
        verify(cartRepository).save(cart);
    }

    @Test
    void getOrCreateActiveCart_shouldCreateCart_whenCustomerHasNoActiveCart() {
        // Given
        UUID customerUserId = UUID.randomUUID();
        CustomerProfile customer = customerProfile(UUID.randomUUID());

        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(cartRepository.findByCustomerProfileIdAndStatus(customer.getId(), CartStatus.ACTIVE)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        // When
        Cart cart = cartService.getOrCreateActiveCart(customerUserId);

        // Then
        assertThat(cart.getCustomerProfile()).isSameAs(customer);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.ACTIVE);
        verify(cartRepository).save(cart);
    }

    private void givenActiveCart(UUID customerUserId, CustomerProfile customer, Cart cart) {
        when(customerProfileService.requireByUserId(customerUserId)).thenReturn(customer);
        when(cartRepository.findByCustomerProfileIdAndStatus(customer.getId(), CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
    }

    private CustomerProfile customerProfile(UUID customerId) {
        CustomerProfile customer = new CustomerProfile();
        customer.setId(customerId);
        customer.setFullName("Test Customer");
        return customer;
    }

    private Cart activeCart(UUID cartId, CustomerProfile customer) {
        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setCustomerProfile(customer);
        cart.setStatus(CartStatus.ACTIVE);
        return cart;
    }

    private CartItem cartItem(Cart cart, UUID productId, int quantity) {
        CartItem item = new CartItem();
        item.setId(UUID.randomUUID());
        item.setCart(cart);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private Product product(UUID productId, BigDecimal price) {
        return product(productId, UUID.randomUUID(), price);
    }

    private Product product(UUID productId, UUID categoryId, BigDecimal price) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Electronics");

        Product product = new Product();
        product.setId(productId);
        product.setCategory(category);
        product.setName("Keyboard");
        product.setPrice(price);
        return product;
    }

    private CartPricingResult pricedCart(UUID cartId, String couponCode, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
        return new CartPricingResult(
                cartId,
                subtotal.compareTo(BigDecimal.ZERO) == 0
                        ? List.of()
                        : List.of(new CartPricingResult.CartLinePrice(
                                UUID.randomUUID(),
                                "Keyboard",
                                1,
                                10,
                                CartItemStockStatus.IN_STOCK,
                                subtotal,
                                subtotal,
                                discount,
                                total
                        )),
                couponCode,
                true,
                subtotal,
                discount,
                total
        );
    }

    private EligibleCouponResponse eligibleCoupon(String code, BigDecimal estimatedDiscount) {
        Instant now = Instant.now();
        return new EligibleCouponResponse(
                UUID.randomUUID(),
                code,
                "Test coupon",
                DiscountType.FLAT,
                estimatedDiscount,
                estimatedDiscount,
                BigDecimal.ZERO,
                now.minusSeconds(60),
                now.plusSeconds(3600),
                "LIVE",
                estimatedDiscount,
                true,
                null
        );
    }
}
