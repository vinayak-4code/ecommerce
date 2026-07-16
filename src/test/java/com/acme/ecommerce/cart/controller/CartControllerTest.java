package com.acme.ecommerce.cart.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.cart.dto.ApplyCouponRequest;
import com.acme.ecommerce.cart.dto.CartItemResponse;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.dto.UpdateCartItemQuantityRequest;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.cart.service.CartService;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.coupon.dto.EligibleCouponResponse;
import com.acme.ecommerce.coupon.enums.DiscountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for the customer cart controller.
 *
 * <p>The controller is intentionally thin: it reads the authenticated user from
 * the security context and delegates all cart behavior to {@link CartService}.</p>
 */
@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    private UUID customerUserId;

    @BeforeEach
    void setUp() {
        customerUserId = UUID.randomUUID();
        authenticate(customerUserId, UserRole.CUSTOMER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void view_shouldDelegateToCartServiceUsingAuthenticatedCustomer() {
        // Given
        CartResponse expectedResponse = cartResponse(UUID.randomUUID(), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(cartService.view(customerUserId)).thenReturn(expectedResponse);

        // When
        CartResponse response = cartController.view();

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(cartService).view(customerUserId);
    }

    @Test
    void addItem_shouldDelegateRequestAndAuthenticatedCustomerToCartService() {
        // Given
        UUID productId = UUID.randomUUID();
        AddCartItemRequest request = new AddCartItemRequest(productId, 2);
        CartResponse expectedResponse = cartResponse(UUID.randomUUID(), null, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"));
        when(cartService.addItem(customerUserId, request)).thenReturn(expectedResponse);

        // When
        CartResponse response = cartController.addItem(request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(cartService).addItem(customerUserId, request);
    }

    @Test
    void updateQuantity_shouldDelegateProductIdRequestAndAuthenticatedCustomerToCartService() {
        // Given
        UUID productId = UUID.randomUUID();
        UpdateCartItemQuantityRequest request = new UpdateCartItemQuantityRequest(3);
        CartResponse expectedResponse = cartResponse(UUID.randomUUID(), null, new BigDecimal("150.00"), BigDecimal.ZERO, new BigDecimal("150.00"));
        when(cartService.updateQuantity(customerUserId, productId, request)).thenReturn(expectedResponse);

        // When
        CartResponse response = cartController.updateQuantity(productId, request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(cartService).updateQuantity(customerUserId, productId, request);
    }

    @Test
    void removeItem_shouldDelegateProductIdAndAuthenticatedCustomerToCartService() {
        // Given
        UUID productId = UUID.randomUUID();
        CartResponse expectedResponse = cartResponse(UUID.randomUUID(), null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(cartService.removeItem(customerUserId, productId)).thenReturn(expectedResponse);

        // When
        CartResponse response = cartController.removeItem(productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(cartService).removeItem(customerUserId, productId);
    }

    @Test
    void eligibleCoupons_shouldDelegateAuthenticatedCustomerToCartService() {
        // Given
        EligibleCouponResponse eligibleCoupon = eligibleCoupon("SAVE10", new BigDecimal("10.00"));
        when(cartService.eligibleCoupons(customerUserId)).thenReturn(List.of(eligibleCoupon));

        // When
        List<EligibleCouponResponse> response = cartController.eligibleCoupons();

        // Then
        assertThat(response).containsExactly(eligibleCoupon);
        verify(cartService).eligibleCoupons(customerUserId);
    }

    @Test
    void applyCoupon_shouldDelegateRequestAndAuthenticatedCustomerToCartService() {
        // Given
        ApplyCouponRequest request = new ApplyCouponRequest("SAVE10");
        CartResponse expectedResponse = cartResponse(UUID.randomUUID(), "SAVE10", new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("90.00"));
        when(cartService.applyCoupon(customerUserId, request)).thenReturn(expectedResponse);

        // When
        CartResponse response = cartController.applyCoupon(request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(cartService).applyCoupon(customerUserId, request);
    }

    @Test
    void removeCoupon_shouldDelegateAuthenticatedCustomerToCartService() {
        // Given
        CartResponse expectedResponse = cartResponse(UUID.randomUUID(), null, new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"));
        when(cartService.removeCoupon(customerUserId)).thenReturn(expectedResponse);

        // When
        CartResponse response = cartController.removeCoupon();

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(cartService).removeCoupon(customerUserId);
    }

    private void authenticate(UUID userId, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "user@example.com", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private CartResponse cartResponse(UUID cartId, String couponCode, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
        List<CartItemResponse> items = subtotal.compareTo(BigDecimal.ZERO) == 0
                ? List.of()
                : List.of(new CartItemResponse(
                        UUID.randomUUID(),
                        "Keyboard",
                        1,
                        10,
                        CartItemStockStatus.IN_STOCK,
                        subtotal,
                        subtotal,
                        discount,
                        total
                ));
        return new CartResponse(cartId, items, couponCode, true, subtotal, discount, total);
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
