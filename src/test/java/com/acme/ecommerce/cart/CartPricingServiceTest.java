package com.acme.ecommerce.cart;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.cart.service.CartPricingResult;
import com.acme.ecommerce.cart.service.CartPricingService;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.coupon.strategy.DiscountStrategyFactory;
import com.acme.ecommerce.coupon.strategy.FlatDiscountStrategy;
import com.acme.ecommerce.coupon.strategy.PercentageDiscountStrategy;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartPricingServiceTest {
    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponService couponService;

    @Mock
    private CouponValidator couponValidator;

    private CartPricingService cartPricingService;

    @BeforeEach
    void setUp() {
        DiscountStrategyFactory factory = new DiscountStrategyFactory(new PercentageDiscountStrategy(), new FlatDiscountStrategy());
        cartPricingService = new CartPricingService(cartItemRepository, productRepository, couponService, factory, couponValidator);
    }

    @Test
    void distributesCartLevelPercentageDiscountProportionallyAcrossItems() {
        UUID productAId = UUID.randomUUID();
        UUID productBId = UUID.randomUUID();
        Cart cart = cart("SAVE10");
        CartItem itemA = item(cart, productAId, 1);
        CartItem itemB = item(cart, productBId, 3);
        Product productA = product(productAId, "Phone", "100.00");
        Product productB = product(productBId, "Laptop", "100.00");
        Coupon coupon = coupon("SAVE10", DiscountType.PERCENTAGE, DiscountScope.CART, "10.00");

        when(cartItemRepository.findByCartId(cart.getId())).thenReturn(List.of(itemA, itemB));
        when(productRepository.findByIdIn(any())).thenReturn(List.of(productA, productB));
        when(couponService.requireActiveByCode("SAVE10")).thenReturn(coupon);
        doNothing().when(couponValidator).validate(any(), any(), any());

        CartPricingResult result = cartPricingService.price(cart);

        assertThat(result.subtotal()).isEqualByComparingTo("400.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("40.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("360.00");
        assertThat(result.lines()).extracting(CartPricingResult.CartLinePrice::discountAmount)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("30.00"));
    }

    private Cart cart(String couponCode) {
        Cart cart = new Cart();
        cart.setId(UUID.randomUUID());
        cart.setCouponCode(couponCode);
        return cart;
    }

    private CartItem item(Cart cart, UUID productId, int quantity) {
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProductId(productId);
        item.setQuantity(quantity);
        return item;
    }

    private Product product(UUID productId, String name, String price) {
        Product product = new Product();
        product.setId(productId);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setCurrency(CurrencyCode.INR);
        product.setStatus(ProductStatus.PUBLISHED);
        return product;
    }

    private Coupon coupon(String code, DiscountType type, DiscountScope scope, String value) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(type);
        coupon.setDiscountScope(scope);
        coupon.setValue(new BigDecimal(value));
        return coupon;
    }
}
