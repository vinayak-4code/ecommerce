package com.acme.ecommerce.cart;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.cart.service.CartPricingResult;
import com.acme.ecommerce.cart.service.CartPricingService;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.coupon.strategy.DiscountStrategyFactory;
import com.acme.ecommerce.coupon.strategy.FlatDiscountStrategy;
import com.acme.ecommerce.coupon.strategy.UpToPercentOffDiscountStrategy;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import com.acme.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for CartPricingService covering:
 * - Empty cart handling
 * - Multi-item subtotal calculation
 * - Flat discount (capped at eligible amount)
 * - Percentage discount with max cap
 * - Discount only applies to enrolled+eligible products (not all)
 * - Proportional discount distribution across multiple eligible items
 * - Coupon that exceeds cart value is capped
 * - Cart with out-of-stock items is not checkout-ready
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartPricingService – Coupon & Discount Math")
class CartPricingServiceCouponTest {
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CouponService couponService;
    @Mock private CouponValidator couponValidator;
    @Mock private InventoryService inventoryService;

    private CartPricingService pricingService;

    @BeforeEach
    void setUp() {
        DiscountStrategyFactory factory = new DiscountStrategyFactory(new UpToPercentOffDiscountStrategy(), new FlatDiscountStrategy());
        pricingService = new CartPricingService(cartItemRepository, productRepository, couponService, factory, couponValidator, inventoryService);
    }

    // ===== HELPERS =====
    private Cart cart(String couponCode) {
        Cart c = new Cart(); c.setId(UUID.randomUUID()); c.setCouponCode(couponCode); return c;
    }
    private CartItem item(Cart cart, UUID productId, int qty) {
        CartItem i = new CartItem(); i.setCart(cart); i.setProductId(productId); i.setQuantity(qty); return i;
    }
    private Product product(UUID id, String name, String price, UUID categoryId) {
        Category cat = new Category(); cat.setId(categoryId);
        Product p = new Product(); p.setId(id); p.setName(name); p.setCategory(cat);
        p.setPrice(new BigDecimal(price)); p.setCurrency(CurrencyCode.INR); p.setStatus(ProductStatus.PUBLISHED);
        return p;
    }
    private Coupon coupon(String code, DiscountType type, String value, String maxDiscount) {
        Coupon c = new Coupon(); c.setId(UUID.randomUUID()); c.setCode(code);
        c.setDiscountType(type); c.setDiscountScope(DiscountScope.CATEGORY);
        c.setValue(new BigDecimal(value));
        c.setMaxDiscountAmount(maxDiscount != null ? new BigDecimal(maxDiscount) : null);
        c.setStartsAt(Instant.now().minusSeconds(3600)); c.setEndsAt(Instant.now().plusSeconds(3600));
        return c;
    }
    private void mockInventoryInStock(UUID... productIds) {
        for (UUID id : productIds) when(inventoryService.consolidatedAvailable(id)).thenReturn(100L);
    }

    // ===== TESTS =====

    @Nested
    @DisplayName("Empty Cart")
    class EmptyCart {
        @Test
        @DisplayName("returns zero totals and no lines for empty cart")
        void emptyCartReturnsZeros() {
            Cart cart = cart(null);
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of());

            CartPricingResult result = pricingService.price(cart);

            assertThat(result.lines()).isEmpty();
            assertThat(result.subtotal()).isEqualByComparingTo("0.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
            assertThat(result.checkoutReady()).isFalse();
        }

        @Test
        @DisplayName("empty cart with coupon code still returns zero (coupon ignored)")
        void emptyCartWithCouponStillZero() {
            Cart cart = cart("ELECTRO10");
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of());

            CartPricingResult result = pricingService.price(cart);

            assertThat(result.subtotal()).isEqualByComparingTo("0.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Subtotal Calculation (No Coupon)")
    class SubtotalCalculation {
        @Test
        @DisplayName("single item: price × quantity = subtotal")
        void singleItem() {
            UUID productId = UUID.randomUUID();
            UUID categoryId = UUID.randomUUID();
            Cart cart = cart(null);
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, productId, 3)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(product(productId, "Phone", "499.99", categoryId)));
            mockInventoryInStock(productId);

            CartPricingResult result = pricingService.price(cart);

            assertThat(result.subtotal()).isEqualByComparingTo("1499.97");
            assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("1499.97");
            assertThat(result.checkoutReady()).isTrue();
        }

        @Test
        @DisplayName("multiple items sum correctly")
        void multipleItems() {
            UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart(null);
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(
                    item(cart, p1, 1), item(cart, p2, 2), item(cart, p3, 5)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(
                    product(p1, "A", "100.00", catId), product(p2, "B", "250.00", catId), product(p3, "C", "10.00", catId)));
            mockInventoryInStock(p1, p2, p3);

            CartPricingResult result = pricingService.price(cart);

            // 100 + 500 + 50 = 650
            assertThat(result.subtotal()).isEqualByComparingTo("650.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("650.00");
        }
    }

    @Nested
    @DisplayName("Flat Discount")
    class FlatDiscount {
        @Test
        @DisplayName("flat ₹200 off applied to eligible items only")
        void flatDiscountApplied() {
            UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart("FLAT200");
            Coupon coupon = coupon("FLAT200", DiscountType.FLAT, "200.00", null);

            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 2), item(cart, p2, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(
                    product(p1, "Phone", "500.00", catId), product(p2, "Case", "100.00", catId)));
            mockInventoryInStock(p1, p2);
            when(couponService.requireActiveByCode("FLAT200")).thenReturn(coupon);
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(p1, p2));
            when(couponService.isCategoryEligible(eq(coupon), any())).thenReturn(true);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            // subtotal: 1000 + 100 = 1100, flat 200 off
            assertThat(result.subtotal()).isEqualByComparingTo("1100.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("200.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("900.00");
        }

        @Test
        @DisplayName("flat discount cannot exceed eligible amount (caps at cart value)")
        void flatDiscountCappedAtEligibleAmount() {
            UUID p1 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart("BIGFLAT");
            Coupon coupon = coupon("BIGFLAT", DiscountType.FLAT, "5000.00", null);

            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(product(p1, "Shirt", "150.00", catId)));
            mockInventoryInStock(p1);
            when(couponService.requireActiveByCode("BIGFLAT")).thenReturn(coupon);
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(p1));
            when(couponService.isCategoryEligible(eq(coupon), any())).thenReturn(true);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            // discount capped at eligible amount = 150
            assertThat(result.subtotal()).isEqualByComparingTo("150.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("150.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Percentage Discount with Max Cap")
    class PercentageDiscount {
        @Test
        @DisplayName("10% off ₹50000 eligible, capped at ₹500 max discount")
        void percentageCappedByMax() {
            UUID p1 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart("ELECTRO10");
            Coupon coupon = coupon("ELECTRO10", DiscountType.UPTO_PERCENT_OFF, "10.00", "500.00");

            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(product(p1, "Phone", "50000.00", catId)));
            mockInventoryInStock(p1);
            when(couponService.requireActiveByCode("ELECTRO10")).thenReturn(coupon);
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(p1));
            when(couponService.isCategoryEligible(eq(coupon), any())).thenReturn(true);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            // 10% of 50000 = 5000, but capped at 500
            assertThat(result.subtotal()).isEqualByComparingTo("50000.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("500.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("49500.00");
        }

        @Test
        @DisplayName("10% off ₹2000 eligible (200 discount, under cap of 500)")
        void percentageUnderCap() {
            UUID p1 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart("ELECTRO10");
            Coupon coupon = coupon("ELECTRO10", DiscountType.UPTO_PERCENT_OFF, "10.00", "500.00");

            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 2)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(product(p1, "Earbuds", "1000.00", catId)));
            mockInventoryInStock(p1);
            when(couponService.requireActiveByCode("ELECTRO10")).thenReturn(coupon);
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(p1));
            when(couponService.isCategoryEligible(eq(coupon), any())).thenReturn(true);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            // 10% of 2000 = 200 (under cap of 500)
            assertThat(result.subtotal()).isEqualByComparingTo("2000.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("200.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("1800.00");
        }

        @Test
        @DisplayName("percentage discount with no max cap uses full percentage")
        void percentageNoCap() {
            UUID p1 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart("HALFOFF");
            Coupon coupon = coupon("HALFOFF", DiscountType.UPTO_PERCENT_OFF, "50.00", null);

            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(product(p1, "Laptop", "80000.00", catId)));
            mockInventoryInStock(p1);
            when(couponService.requireActiveByCode("HALFOFF")).thenReturn(coupon);
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(p1));
            when(couponService.isCategoryEligible(eq(coupon), any())).thenReturn(true);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            // 50% of 80000 = 40000 (no cap)
            assertThat(result.subtotal()).isEqualByComparingTo("80000.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("40000.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("40000.00");
        }
    }

    @Nested
    @DisplayName("Partial Eligibility – Only Enrolled Products Get Discount")
    class PartialEligibility {
        @Test
        @DisplayName("discount applies only to enrolled products, not all items in cart")
        void onlyEnrolledProductsGetDiscount() {
            UUID enrolled = UUID.randomUUID(), notEnrolled = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart("FLAT100");
            Coupon coupon = coupon("FLAT100", DiscountType.FLAT, "100.00", null);

            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(
                    item(cart, enrolled, 1), item(cart, notEnrolled, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(
                    product(enrolled, "Phone", "500.00", catId), product(notEnrolled, "Shirt", "300.00", catId)));
            mockInventoryInStock(enrolled, notEnrolled);
            when(couponService.requireActiveByCode("FLAT100")).thenReturn(coupon);
            // Only phone is enrolled
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(enrolled));
            when(couponService.isCategoryEligible(eq(coupon), any())).thenReturn(true);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            // subtotal: 800, discount: 100 (only on enrolled item)
            assertThat(result.subtotal()).isEqualByComparingTo("800.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("100.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("700.00");

            // Enrolled line gets the discount
            CartPricingResult.CartLinePrice enrolledLine = result.lines().stream()
                    .filter(l -> l.productId().equals(enrolled)).findFirst().orElseThrow();
            assertThat(enrolledLine.discountAmount()).isEqualByComparingTo("100.00");
            assertThat(enrolledLine.totalAmount()).isEqualByComparingTo("400.00");

            // Non-enrolled line gets zero discount
            CartPricingResult.CartLinePrice notEnrolledLine = result.lines().stream()
                    .filter(l -> l.productId().equals(notEnrolled)).findFirst().orElseThrow();
            assertThat(notEnrolledLine.discountAmount()).isEqualByComparingTo("0.00");
            assertThat(notEnrolledLine.totalAmount()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("category-ineligible products do not receive discount even if enrolled")
        void categoryIneligibleNoDiscount() {
            UUID electronics = UUID.randomUUID(), apparel = UUID.randomUUID();
            UUID electronicsCat = UUID.randomUUID(), apparelCat = UUID.randomUUID();
            Cart cart = cart("ELECTRO10");
            Coupon coupon = coupon("ELECTRO10", DiscountType.UPTO_PERCENT_OFF, "10.00", "500.00");

            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(
                    item(cart, electronics, 1), item(cart, apparel, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(
                    product(electronics, "Phone", "10000.00", electronicsCat),
                    product(apparel, "Shirt", "1500.00", apparelCat)));
            mockInventoryInStock(electronics, apparel);
            when(couponService.requireActiveByCode("ELECTRO10")).thenReturn(coupon);
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(electronics, apparel));
            // Only electronics category is eligible
            when(couponService.isCategoryEligible(eq(coupon), eq(electronicsCat))).thenReturn(true);
            when(couponService.isCategoryEligible(eq(coupon), eq(apparelCat))).thenReturn(false);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            // 10% of 10000 = 1000, capped at 500
            assertThat(result.discountAmount()).isEqualByComparingTo("500.00");
            assertThat(result.subtotal()).isEqualByComparingTo("11500.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("11000.00");
        }
    }

    @Nested
    @DisplayName("Proportional Discount Distribution")
    class ProportionalDistribution {
        @Test
        @DisplayName("discount is distributed proportionally across multiple eligible items")
        void proportionalDistribution() {
            UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID(), p3 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart("FLAT300");
            Coupon coupon = coupon("FLAT300", DiscountType.FLAT, "300.00", null);

            // p1: 1×1000=1000, p2: 2×500=1000, p3: 1×2000=2000 (total eligible: 4000)
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(
                    item(cart, p1, 1), item(cart, p2, 2), item(cart, p3, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(
                    product(p1, "A", "1000.00", catId), product(p2, "B", "500.00", catId), product(p3, "C", "2000.00", catId)));
            mockInventoryInStock(p1, p2, p3);
            when(couponService.requireActiveByCode("FLAT300")).thenReturn(coupon);
            when(couponService.enrolledProductIds(eq(coupon.getId()), any())).thenReturn(Set.of(p1, p2, p3));
            when(couponService.isCategoryEligible(eq(coupon), any())).thenReturn(true);
            doNothing().when(couponValidator).validateUsableNow(eq(coupon), any());

            CartPricingResult result = pricingService.price(cart);

            assertThat(result.subtotal()).isEqualByComparingTo("4000.00");
            assertThat(result.discountAmount()).isEqualByComparingTo("300.00");
            assertThat(result.totalAmount()).isEqualByComparingTo("3700.00");

            // Sum of line discounts must equal total discount (no rounding loss)
            BigDecimal sumLineDiscounts = result.lines().stream()
                    .map(CartPricingResult.CartLinePrice::discountAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(MoneyUtil.money(sumLineDiscounts)).isEqualByComparingTo("300.00");
        }
    }

    @Nested
    @DisplayName("Stock Status & Checkout Readiness")
    class StockStatus {
        @Test
        @DisplayName("out-of-stock item makes cart not checkout-ready")
        void outOfStockNotReady() {
            UUID p1 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart(null);
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 2)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(product(p1, "Phone", "500.00", catId)));
            when(inventoryService.consolidatedAvailable(p1)).thenReturn(0L);

            CartPricingResult result = pricingService.price(cart);

            assertThat(result.checkoutReady()).isFalse();
            assertThat(result.lines().getFirst().stockStatus()).isEqualTo(CartItemStockStatus.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("requested quantity > available marks INSUFFICIENT_STOCK")
        void insufficientStock() {
            UUID p1 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart(null);
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 10)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(product(p1, "Laptop", "80000.00", catId)));
            when(inventoryService.consolidatedAvailable(p1)).thenReturn(3L);

            CartPricingResult result = pricingService.price(cart);

            assertThat(result.checkoutReady()).isFalse();
            assertThat(result.lines().getFirst().stockStatus()).isEqualTo(CartItemStockStatus.INSUFFICIENT_STOCK);
            assertThat(result.lines().getFirst().availableQuantity()).isEqualTo(3L);
        }

        @Test
        @DisplayName("all items in stock = checkout ready")
        void allInStock() {
            UUID p1 = UUID.randomUUID(), p2 = UUID.randomUUID();
            UUID catId = UUID.randomUUID();
            Cart cart = cart(null);
            when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(item(cart, p1, 2), item(cart, p2, 1)));
            when(productRepository.findByIdIn(any())).thenReturn(List.of(
                    product(p1, "A", "100.00", catId), product(p2, "B", "200.00", catId)));
            mockInventoryInStock(p1, p2);

            CartPricingResult result = pricingService.price(cart);

            assertThat(result.checkoutReady()).isTrue();
            assertThat(result.lines()).allMatch(l -> l.stockStatus() == CartItemStockStatus.IN_STOCK);
        }
    }
}

