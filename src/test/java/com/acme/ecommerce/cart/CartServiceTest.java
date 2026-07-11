package com.acme.ecommerce.cart;

import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.dto.UpdateCartItemQuantityRequest;
import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.enums.CartStatus;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.cart.repository.CartRepository;
import com.acme.ecommerce.cart.service.CartPricingResult;
import com.acme.ecommerce.cart.service.CartPricingService;
import com.acme.ecommerce.cart.service.CartService;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.service.CustomerProfileService;
import com.acme.ecommerce.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for CartService demonstrating:
 * - Cart isolation: each user gets their own cart
 * - Quantity 0 removes item
 * - Removing the last item leaves a valid empty cart
 * - Stock validation on add
 * - Cart ownership is enforced through customer profile lookup
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService – Cart Isolation & Edge Cases")
class CartServiceTest {
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private CustomerProfileService customerProfileService;
    @Mock private ProductService productService;
    @Mock private CouponService couponService;
    @Mock private CartPricingService cartPricingService;
    @Mock private InventoryService inventoryService;
    @Mock private DomainEventPublisher domainEventPublisher;

    private CartService cartService;

    // Two separate users
    private static final UUID USER_A_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_B_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CUSTOMER_A_PROFILE_ID = UUID.fromString("10000000-0000-0000-0000-000000000011");
    private static final UUID CUSTOMER_B_PROFILE_ID = UUID.fromString("20000000-0000-0000-0000-000000000022");

    private CustomerProfile customerA;
    private CustomerProfile customerB;
    private Cart cartA;
    private Cart cartB;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, customerProfileService,
                productService, couponService, cartPricingService, inventoryService, domainEventPublisher);

        customerA = new CustomerProfile();
        customerA.setId(CUSTOMER_A_PROFILE_ID);

        customerB = new CustomerProfile();
        customerB.setId(CUSTOMER_B_PROFILE_ID);

        cartA = new Cart();
        cartA.setId(UUID.randomUUID());
        cartA.setCustomerProfile(customerA);
        cartA.setStatus(CartStatus.ACTIVE);

        cartB = new Cart();
        cartB.setId(UUID.randomUUID());
        cartB.setCustomerProfile(customerB);
        cartB.setStatus(CartStatus.ACTIVE);
    }

    private CartPricingResult emptyPricingResult(UUID cartId) {
        return new CartPricingResult(cartId, List.of(), null, false, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
    }

    @Nested
    @DisplayName("Cart Isolation Between Users")
    class CartIsolation {
        @Test
        @DisplayName("User A's cart is separate from User B's cart")
        void separateCartsPerUser() {
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(customerProfileService.requireByUserId(USER_B_ID)).thenReturn(customerB);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_B_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartB));
            when(cartPricingService.price(cartA)).thenReturn(emptyPricingResult(cartA.getId()));
            when(cartPricingService.price(cartB)).thenReturn(emptyPricingResult(cartB.getId()));

            CartResponse responseA = cartService.view(USER_A_ID);
            CartResponse responseB = cartService.view(USER_B_ID);

            assertThat(responseA.cartId()).isEqualTo(cartA.getId());
            assertThat(responseB.cartId()).isEqualTo(cartB.getId());
            assertThat(responseA.cartId()).isNotEqualTo(responseB.cartId());
        }

        @Test
        @DisplayName("User A cannot see User B's cart (isolated by customer profile)")
        void userCannotAccessOthersCart() {
            // User A gets their own cart (looked up by their profile)
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            when(cartPricingService.price(cartA)).thenReturn(emptyPricingResult(cartA.getId()));

            CartResponse responseA = cartService.view(USER_A_ID);

            // Verify it's looked up by customer A's profile, not B's
            verify(cartRepository).findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE);
            verify(cartRepository, never()).findByCustomerProfileIdAndStatus(CUSTOMER_B_PROFILE_ID, CartStatus.ACTIVE);
            assertThat(responseA.cartId()).isEqualTo(cartA.getId());
        }

        @Test
        @DisplayName("adding items to User A's cart does not affect User B")
        void addItemIsolated() {
            UUID productId = UUID.randomUUID();
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            when(cartItemRepository.findByCartIdAndProductId(cartA.getId(), productId)).thenReturn(Optional.empty());
            when(inventoryService.consolidatedAvailable(productId)).thenReturn(10L);
            when(cartPricingService.price(cartA)).thenReturn(emptyPricingResult(cartA.getId()));

            cartService.addItem(USER_A_ID, new AddCartItemRequest(productId, 2));

            // Verify item was saved with correct cart
            ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
            verify(cartItemRepository).save(captor.capture());
            assertThat(captor.getValue().getCart().getId()).isEqualTo(cartA.getId());
        }
    }

    @Nested
    @DisplayName("Quantity Zero Removes Item")
    class QuantityZero {
        @Test
        @DisplayName("setting quantity to 0 removes the item from cart")
        void quantityZeroRemovesItem() {
            UUID productId = UUID.randomUUID();
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            when(cartPricingService.price(cartA)).thenReturn(emptyPricingResult(cartA.getId()));

            cartService.updateQuantity(USER_A_ID, productId, new UpdateCartItemQuantityRequest(0));

            verify(cartItemRepository).deleteByCartIdAndProductId(cartA.getId(), productId);
        }
    }

    @Nested
    @DisplayName("Remove Last Item – Empty Cart Valid")
    class RemoveLastItem {
        @Test
        @DisplayName("removing the only item returns valid empty cart with zero totals")
        void removeLastItemReturnsEmptyCart() {
            UUID productId = UUID.randomUUID();
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            CartPricingResult emptyResult = emptyPricingResult(cartA.getId());
            when(cartPricingService.price(cartA)).thenReturn(emptyResult);

            CartResponse response = cartService.removeItem(USER_A_ID, productId);

            verify(cartItemRepository).deleteByCartIdAndProductId(cartA.getId(), productId);
            assertThat(response.items()).isEmpty();
            assertThat(response.subtotal()).isEqualByComparingTo("0.00");
            assertThat(response.totalAmount()).isEqualByComparingTo("0.00");
            assertThat(response.discountAmount()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Stock Validation")
    class StockValidation {
        @Test
        @DisplayName("adding out-of-stock product throws exception")
        void outOfStockThrows() {
            UUID productId = UUID.randomUUID();
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            when(cartItemRepository.findByCartIdAndProductId(cartA.getId(), productId)).thenReturn(Optional.empty());
            when(inventoryService.consolidatedAvailable(productId)).thenReturn(0L);

            assertThatThrownBy(() -> cartService.addItem(USER_A_ID, new AddCartItemRequest(productId, 1)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("out of stock");
        }

        @Test
        @DisplayName("adding more than available throws exception")
        void exceedsStockThrows() {
            UUID productId = UUID.randomUUID();
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            when(cartItemRepository.findByCartIdAndProductId(cartA.getId(), productId)).thenReturn(Optional.empty());
            when(inventoryService.consolidatedAvailable(productId)).thenReturn(3L);

            assertThatThrownBy(() -> cartService.addItem(USER_A_ID, new AddCartItemRequest(productId, 5)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("exceeds available");
        }
    }

    @Nested
    @DisplayName("Coupon Operations")
    class CouponOps {
        @Test
        @DisplayName("cannot apply second coupon when one is already active")
        void cannotApplySecondCoupon() {
            cartA.setCouponCode("FIRST");
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));

            assertThatThrownBy(() -> cartService.applyCoupon(USER_A_ID,
                    new com.acme.ecommerce.cart.dto.ApplyCouponRequest("SECOND")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("one coupon");
        }

        @Test
        @DisplayName("removing coupon clears coupon code from cart")
        void removeCouponClearsCode() {
            cartA.setCouponCode("ELECTRO10");
            when(customerProfileService.requireByUserId(USER_A_ID)).thenReturn(customerA);
            when(cartRepository.findByCustomerProfileIdAndStatus(CUSTOMER_A_PROFILE_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cartA));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(cartPricingService.price(any(Cart.class))).thenReturn(emptyPricingResult(cartA.getId()));

            cartService.removeCoupon(USER_A_ID);

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            assertThat(captor.getValue().getCouponCode()).isNull();
        }
    }
}

