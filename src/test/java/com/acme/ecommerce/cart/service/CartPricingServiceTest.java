package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.cart.enums.CartStatus;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.coupon.strategy.DiscountStrategy;
import com.acme.ecommerce.coupon.strategy.DiscountStrategyFactory;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import com.acme.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit specification for live cart pricing.
 *
 * <p>Pricing intentionally reloads product price, publication status, coupon
 * state, category eligibility, enrollment, and live inventory on every call.
 * These tests cover the important pricing edges without starting Spring.</p>
 */
@ExtendWith(MockitoExtension.class)
class CartPricingServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private DiscountStrategyFactory discountStrategyFactory;

    @Mock
    private CouponValidator couponValidator;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private CartPricingService cartPricingService;

    @Test
    void price_shouldReturnEmptyResult_whenCartHasNoItems() {
        // Given
        UUID cartId = UUID.randomUUID();
        Cart cart = cart(cartId, null);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of());

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.cartId()).isEqualTo(cartId);
        assertThat(result.lines()).isEmpty();
        assertThat(result.couponCode()).isNull();
        assertThat(result.checkoutReady()).isFalse();
        assertThat(result.subtotal()).isEqualByComparingTo("0.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");

        verify(cartItemRepository).findByCartId(cartId);
        verifyNoInteractions(productRepository, couponService, discountStrategyFactory, couponValidator, inventoryService);
    }

    @Test
    void price_shouldCalculateLineAndCartTotals_whenProductIsPublishedAndCouponIsBlank() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Cart cart = cart(cartId, "   ");
        CartItem item = cartItem(cart, productId, 2);
        Product product = product(productId, categoryId, "Gaming Keyboard", "49.99", ProductStatus.PUBLISHED);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(10L);

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.checkoutReady()).isTrue();
        assertThat(result.subtotal()).isEqualByComparingTo("99.98");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("99.98");

        CartPricingResult.CartLinePrice line = result.lines().get(0);
        assertThat(line.productId()).isEqualTo(productId);
        assertThat(line.productName()).isEqualTo("Gaming Keyboard");
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.availableQuantity()).isEqualTo(10L);
        assertThat(line.stockStatus()).isEqualTo(CartItemStockStatus.IN_STOCK);
        assertThat(line.unitPrice()).isEqualByComparingTo("49.99");
        assertThat(line.subtotal()).isEqualByComparingTo("99.98");
        assertThat(line.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(line.totalAmount()).isEqualByComparingTo("99.98");
    }

    @Test
    void price_shouldMarkLineAsInsufficientStock_whenRequestedQuantityIsGreaterThanAvailableQuantity() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Cart cart = cart(cartId, null);
        CartItem item = cartItem(cart, productId, 5);
        Product product = product(productId, categoryId, "Wireless Mouse", "20.00", ProductStatus.PUBLISHED);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(2L);

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.checkoutReady()).isFalse();
        assertThat(result.subtotal()).isEqualByComparingTo("100.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(result.lines().get(0).stockStatus()).isEqualTo(CartItemStockStatus.INSUFFICIENT_STOCK);
        assertThat(result.lines().get(0).availableQuantity()).isEqualTo(2L);
    }

    @Test
    void price_shouldMarkLineAsOutOfStock_whenAvailableQuantityIsZero() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Cart cart = cart(cartId, null);
        CartItem item = cartItem(cart, productId, 1);
        Product product = product(productId, categoryId, "Laptop Stand", "35.00", ProductStatus.PUBLISHED);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(0L);

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.checkoutReady()).isFalse();
        assertThat(result.lines().get(0).stockStatus()).isEqualTo(CartItemStockStatus.OUT_OF_STOCK);
        assertThat(result.lines().get(0).availableQuantity()).isZero();
    }

    @Test
    void price_shouldMarkLineAsUnavailable_whenCartContainsMissingProduct() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Cart cart = cart(cartId, null);
        CartItem item = cartItem(cart, productId, 1);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of());

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.checkoutReady()).isFalse();
        assertThat(result.subtotal()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).stockStatus()).isEqualTo(CartItemStockStatus.UNAVAILABLE);
        assertThat(result.lines().get(0).productName()).isEqualTo("Unavailable Product");
        assertThat(result.lines().get(0).unitPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void price_shouldMarkLineAsUnavailable_whenProductIsNotPublished() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Cart cart = cart(cartId, null);
        CartItem item = cartItem(cart, productId, 1);
        Product product = product(productId, categoryId, "Draft Product", "15.00", ProductStatus.DRAFT);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.checkoutReady()).isFalse();
        assertThat(result.subtotal()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).stockStatus()).isEqualTo(CartItemStockStatus.UNAVAILABLE);
        assertThat(result.lines().get(0).productName()).isEqualTo("Draft Product");
        assertThat(result.lines().get(0).unitPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void price_shouldApplyDiscount_whenCouponIsValidAndProductIsEligible() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        String couponCode = "SAVE10";

        Cart cart = cart(cartId, couponCode);
        CartItem item = cartItem(cart, productId, 2);
        Product product = product(productId, categoryId, "Keyboard", "50.00", ProductStatus.PUBLISHED);
        Coupon coupon = coupon(couponId, couponCode, DiscountType.FLAT, "10.00");
        DiscountStrategy discountStrategy = mock(DiscountStrategy.class);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(10L);
        when(couponService.requireActiveByCode(couponCode)).thenReturn(coupon);
        when(couponService.enrolledProductIds(couponId, Set.of(productId))).thenReturn(Set.of(productId));
        when(couponService.isCategoryEligible(coupon, categoryId)).thenReturn(true);
        when(discountStrategyFactory.get(DiscountType.FLAT)).thenReturn(discountStrategy);
        when(discountStrategy.calculate(new BigDecimal("100.00"), coupon)).thenReturn(new BigDecimal("10.00"));

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.checkoutReady()).isTrue();
        assertThat(result.subtotal()).isEqualByComparingTo("100.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("90.00");
        assertThat(result.lines().get(0).discountAmount()).isEqualByComparingTo("10.00");
        assertThat(result.lines().get(0).totalAmount()).isEqualByComparingTo("90.00");

        verify(couponValidator).validateUsableNow(coupon, new BigDecimal("100.00"));
    }

    @Test
    void price_shouldReturnZeroDiscount_whenCouponValidationFailsAfterCartChanged() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        String couponCode = "EXPIRED";

        Cart cart = cart(cartId, couponCode);
        CartItem item = cartItem(cart, productId, 1);
        Product product = product(productId, categoryId, "Keyboard", "100.00", ProductStatus.PUBLISHED);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(10L);
        when(couponService.requireActiveByCode(couponCode)).thenThrow(new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Coupon has expired"));

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.couponCode()).isEqualTo(couponCode);
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("100.00");
        verifyNoInteractions(discountStrategyFactory);
    }

    @Test
    void price_shouldReturnZeroDiscount_whenCouponHasNoEligibleEnrolledProducts() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        String couponCode = "SAVE10";

        Cart cart = cart(cartId, couponCode);
        CartItem item = cartItem(cart, productId, 1);
        Product product = product(productId, categoryId, "Keyboard", "100.00", ProductStatus.PUBLISHED);
        Coupon coupon = coupon(couponId, couponCode, DiscountType.FLAT, "10.00");

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(10L);
        when(couponService.requireActiveByCode(couponCode)).thenReturn(coupon);
        when(couponService.enrolledProductIds(couponId, Set.of(productId))).thenReturn(Set.of());

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("100.00");
        verifyNoInteractions(discountStrategyFactory);
    }

    @Test
    void price_shouldAllocateDiscountProportionallyAcrossEligibleLines() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        UUID categoryId1 = UUID.randomUUID();
        UUID categoryId2 = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        String couponCode = "SAVE30";

        Cart cart = cart(cartId, couponCode);
        CartItem item1 = cartItem(cart, productId1, 1);
        CartItem item2 = cartItem(cart, productId2, 2);
        Product product1 = product(productId1, categoryId1, "Keyboard", "100.00", ProductStatus.PUBLISHED);
        Product product2 = product(productId2, categoryId2, "Mouse", "50.00", ProductStatus.PUBLISHED);
        Coupon coupon = coupon(couponId, couponCode, DiscountType.FLAT, "30.00");
        DiscountStrategy discountStrategy = mock(DiscountStrategy.class);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item1, item2));
        when(productRepository.findByIdIn(Set.of(productId1, productId2))).thenReturn(List.of(product1, product2));
        when(inventoryService.consolidatedAvailable(productId1)).thenReturn(3L);
        when(inventoryService.consolidatedAvailable(productId2)).thenReturn(5L);
        when(couponService.requireActiveByCode(couponCode)).thenReturn(coupon);
        when(couponService.enrolledProductIds(couponId, Set.of(productId1, productId2))).thenReturn(Set.of(productId1, productId2));
        when(couponService.isCategoryEligible(coupon, categoryId1)).thenReturn(true);
        when(couponService.isCategoryEligible(coupon, categoryId2)).thenReturn(true);
        when(discountStrategyFactory.get(DiscountType.FLAT)).thenReturn(discountStrategy);
        when(discountStrategy.calculate(new BigDecimal("200.00"), coupon)).thenReturn(new BigDecimal("30.00"));

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.subtotal()).isEqualByComparingTo("200.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("30.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("170.00");

        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).discountAmount()).isEqualByComparingTo("15.00");
        assertThat(result.lines().get(0).totalAmount()).isEqualByComparingTo("85.00");
        assertThat(result.lines().get(1).discountAmount()).isEqualByComparingTo("15.00");
        assertThat(result.lines().get(1).totalAmount()).isEqualByComparingTo("85.00");
    }

    @Test
    void price_shouldClampDiscountToEligibleSubtotal_whenStrategyReturnsTooMuchDiscount() {
        // Given
        UUID cartId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        String couponCode = "BIGSAVE";

        Cart cart = cart(cartId, couponCode);
        CartItem item = cartItem(cart, productId, 1);
        Product product = product(productId, categoryId, "Keyboard", "100.00", ProductStatus.PUBLISHED);
        Coupon coupon = coupon(couponId, couponCode, DiscountType.FLAT, "150.00");
        DiscountStrategy discountStrategy = mock(DiscountStrategy.class);

        when(cartItemRepository.findByCartId(cartId)).thenReturn(List.of(item));
        when(productRepository.findByIdIn(Set.of(productId))).thenReturn(List.of(product));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(10L);
        when(couponService.requireActiveByCode(couponCode)).thenReturn(coupon);
        when(couponService.enrolledProductIds(couponId, Set.of(productId))).thenReturn(Set.of(productId));
        when(couponService.isCategoryEligible(coupon, categoryId)).thenReturn(true);
        when(discountStrategyFactory.get(DiscountType.FLAT)).thenReturn(discountStrategy);
        when(discountStrategy.calculate(new BigDecimal("100.00"), coupon)).thenReturn(new BigDecimal("150.00"));

        // When
        CartPricingResult result = cartPricingService.price(cart);

        // Then
        assertThat(result.discountAmount()).isEqualByComparingTo("100.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
        assertThat(result.lines().get(0).discountAmount()).isEqualByComparingTo("100.00");
        assertThat(result.lines().get(0).totalAmount()).isEqualByComparingTo("0.00");
    }

    private Cart cart(UUID cartId, String couponCode) {
        Cart cart = new Cart();
        cart.setId(cartId);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setCouponCode(couponCode);
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

    private Product product(UUID productId, UUID categoryId, String name, String price, ProductStatus status) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Electronics");

        Product product = new Product();
        product.setId(productId);
        product.setCategory(category);
        product.setName(name);
        product.setSku("SKU-" + productId.toString().substring(0, 8));
        product.setPrice(new BigDecimal(price));
        product.setCurrency(CurrencyCode.INR);
        product.setStatus(status);
        return product;
    }

    private Coupon coupon(UUID couponId, String code, DiscountType discountType, String value) {
        Instant now = Instant.now();
        Coupon coupon = new Coupon();
        coupon.setId(couponId);
        coupon.setCode(code);
        coupon.setDescription("Test coupon");
        coupon.setDiscountType(discountType);
        coupon.setDiscountScope(DiscountScope.CART);
        coupon.setValue(new BigDecimal(value));
        coupon.setMaxDiscountAmount(new BigDecimal(value));
        coupon.setMinCartAmount(BigDecimal.ZERO);
        coupon.setStatus(CouponStatus.ACTIVE);
        coupon.setStartsAt(now.minusSeconds(60));
        coupon.setEndsAt(now.plusSeconds(3600));
        return coupon;
    }
}
