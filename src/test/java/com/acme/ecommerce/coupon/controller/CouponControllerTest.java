package com.acme.ecommerce.coupon.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.coupon.dto.CouponEnrollmentResponse;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.dto.EligibleCouponResponse;
import com.acme.ecommerce.coupon.dto.UpdateCouponRequest;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.service.CouponService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for coupon controller delegation.
 *
 * <p>The controller exposes product-admin coupon management, public eligibility,
 * and seller enrollment endpoints while keeping business rules in CouponService.</p>
 */
@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private CouponService couponService;

    @InjectMocks
    private CouponController couponController;

    private UUID sellerUserId;

    @BeforeEach
    void setUp() {
        sellerUserId = UUID.randomUUID();
        authenticate(sellerUserId, UserRole.SELLER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_shouldDelegateCreateRequestToCouponService() {
        // Given
        CreateCouponRequest request = createCouponRequest("SAVE10");
        CouponResponse expectedResponse = couponResponse(UUID.randomUUID(), "SAVE10");
        when(couponService.create(request)).thenReturn(expectedResponse);

        // When
        CouponResponse response = couponController.create(request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(couponService).create(request);
    }

    @Test
    void update_shouldDelegateCouponIdAndRequestToCouponService() {
        // Given
        UUID couponId = UUID.randomUUID();
        UpdateCouponRequest request = updateCouponRequest("SAVE20");
        CouponResponse expectedResponse = couponResponse(couponId, "SAVE20");
        when(couponService.update(couponId, request)).thenReturn(expectedResponse);

        // When
        CouponResponse response = couponController.update(couponId, request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(couponService).update(couponId, request);
    }

    @Test
    void list_shouldDelegatePaginationToCouponService() {
        // Given
        CouponResponse coupon = couponResponse(UUID.randomUUID(), "SAVE10");
        Page<CouponResponse> expectedPage = new PageImpl<>(List.of(coupon));
        when(couponService.list(0, 20)).thenReturn(expectedPage);

        // When
        Page<CouponResponse> response = couponController.list(0, 20);

        // Then
        assertThat(response).isSameAs(expectedPage);
        verify(couponService).list(0, 20);
    }

    @Test
    void eligibleForProduct_shouldDelegateProductIdToCouponService() {
        // Given
        UUID productId = UUID.randomUUID();
        EligibleCouponResponse eligibleCoupon = eligibleCoupon("SAVE10", new BigDecimal("10.00"));
        when(couponService.eligibleForProduct(productId)).thenReturn(List.of(eligibleCoupon));

        // When
        List<EligibleCouponResponse> response = couponController.eligibleForProduct(productId);

        // Then
        assertThat(response).containsExactly(eligibleCoupon);
        verify(couponService).eligibleForProduct(productId);
    }

    @Test
    void get_shouldDelegateCodeToCouponService() {
        // Given
        CouponResponse expectedResponse = couponResponse(UUID.randomUUID(), "SAVE10");
        when(couponService.getByCode("save10")).thenReturn(expectedResponse);

        // When
        CouponResponse response = couponController.get("save10");

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(couponService).getByCode("save10");
    }

    @Test
    void enroll_shouldDelegateAuthenticatedSellerCodeAndProductIdToCouponService() {
        // Given
        String code = "SAVE10";
        UUID productId = UUID.randomUUID();
        CouponEnrollmentResponse expectedResponse = new CouponEnrollmentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                code,
                productId,
                UUID.randomUUID(),
                Instant.now()
        );
        when(couponService.enrollProduct(sellerUserId, code, productId)).thenReturn(expectedResponse);

        // When
        CouponEnrollmentResponse response = couponController.enroll(code, productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(couponService).enrollProduct(sellerUserId, code, productId);
    }

    @Test
    void unenroll_shouldDelegateAuthenticatedSellerCodeAndProductIdToCouponService() {
        // Given
        String code = "SAVE10";
        UUID productId = UUID.randomUUID();

        // When
        couponController.unenroll(code, productId);

        // Then
        verify(couponService).unenrollProduct(sellerUserId, code, productId);
    }

    private void authenticate(UUID userId, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "seller@example.com", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private CreateCouponRequest createCouponRequest(String code) {
        Instant now = Instant.now();
        return new CreateCouponRequest(
                code,
                "Test coupon",
                DiscountType.FLAT,
                DiscountScope.CART,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                now.minusSeconds(60),
                now.plusSeconds(3600),
                Set.of()
        );
    }

    private UpdateCouponRequest updateCouponRequest(String code) {
        Instant now = Instant.now();
        return new UpdateCouponRequest(
                code,
                "Updated coupon",
                DiscountType.FLAT,
                DiscountScope.CART,
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CouponStatus.ACTIVE,
                now.minusSeconds(60),
                now.plusSeconds(3600),
                Set.of()
        );
    }

    private CouponResponse couponResponse(UUID couponId, String code) {
        Instant now = Instant.now();
        return new CouponResponse(
                couponId,
                code,
                "Test coupon",
                DiscountType.FLAT,
                DiscountScope.CART,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                CouponStatus.ACTIVE,
                now.minusSeconds(60),
                now.plusSeconds(3600),
                Set.of(),
                Set.of()
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
