package com.acme.ecommerce.coupon.service;

import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.service.CategoryService;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ForbiddenOperationException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.coupon.dto.CouponEnrollmentResponse;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.dto.EligibleCouponResponse;
import com.acme.ecommerce.coupon.dto.UpdateCouponRequest;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.entity.CouponCategoryEligibility;
import com.acme.ecommerce.coupon.entity.CouponProductEnrollment;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.repository.CouponCategoryEligibilityRepository;
import com.acme.ecommerce.coupon.repository.CouponProductEnrollmentRepository;
import com.acme.ecommerce.coupon.repository.CouponRepository;
import com.acme.ecommerce.coupon.strategy.DiscountStrategy;
import com.acme.ecommerce.coupon.strategy.DiscountStrategyFactory;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.service.SellerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit specification for coupon lifecycle, eligibility, and seller enrollment.
 *
 * <p>Coupons are product-admin governed, seller-enrolled, and customer-applied.
 * The tests focus on orchestration and edge rules while repositories, DTOs, and
 * mapper-style response conversion are not tested independently.</p>
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private CouponCategoryEligibilityRepository categoryEligibilityRepository;

    @Mock
    private CouponProductEnrollmentRepository enrollmentRepository;

    @Mock
    private ProductService productService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private SellerService sellerService;

    @Mock
    private CouponValidator couponValidator;

    @Mock
    private DiscountStrategyFactory discountStrategyFactory;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private CouponService couponService;

    @Test
    void create_shouldNormalizeCodeValidateCategoryEligibilitySaveAndPublishEvent() {
        // Given
        UUID couponId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Instant startsAt = Instant.now().minusSeconds(60);
        Instant endsAt = Instant.now().plusSeconds(3600);
        CreateCouponRequest request = new CreateCouponRequest(
                " save10 ",
                "Save on electronics",
                DiscountType.UPTO_PERCENT_OFF,
                DiscountScope.CATEGORY,
                new BigDecimal("10"),
                new BigDecimal("100"),
                new BigDecimal("500"),
                startsAt,
                endsAt,
                Set.of(categoryId)
        );

        when(couponRepository.existsByCodeIgnoreCase("SAVE10")).thenReturn(false);
        when(categoryService.requireCategory(categoryId)).thenReturn(category(categoryId));
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon saved = invocation.getArgument(0);
            saved.setId(couponId);
            return saved;
        });
        when(categoryEligibilityRepository.findByCouponId(couponId)).thenReturn(List.of(categoryEligibility(categoryId)));
        when(enrollmentRepository.findByCouponId(couponId)).thenReturn(List.of());

        // When
        CouponResponse response = couponService.create(request);

        // Then
        assertThat(response.id()).isEqualTo(couponId);
        assertThat(response.code()).isEqualTo("SAVE10");
        assertThat(response.discountType()).isEqualTo(DiscountType.UPTO_PERCENT_OFF);
        assertThat(response.discountScope()).isEqualTo(DiscountScope.CATEGORY);
        assertThat(response.eligibleCategoryIds()).containsExactly(categoryId);

        ArgumentCaptor<Coupon> savedCoupon = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(savedCoupon.capture());
        assertThat(savedCoupon.getValue().getCode()).isEqualTo("SAVE10");
        assertThat(savedCoupon.getValue().getValue()).isEqualByComparingTo("10.00");
        assertThat(savedCoupon.getValue().getMaxDiscountAmount()).isEqualByComparingTo("100.00");
        assertThat(savedCoupon.getValue().getMinCartAmount()).isEqualByComparingTo("500.00");

        verify(couponValidator).validateDefinition(savedCoupon.getValue());
        verify(categoryEligibilityRepository).deleteByCouponId(couponId);
        verify(categoryEligibilityRepository).flush();
        verify(categoryService).requireCategory(categoryId);
        verify(categoryEligibilityRepository).saveAll(any());
        verify(domainEventPublisher).publish(eq(couponId), eq("Coupon"), eq(DomainEventType.COUPON_CREATED), anyMap());
    }

    @Test
    void create_shouldThrowDuplicateResourceException_whenNormalizedCodeAlreadyExists() {
        // Given
        CreateCouponRequest request = createCouponRequest(" save10 ", DiscountScope.CART, Set.of());

        when(couponRepository.existsByCodeIgnoreCase("SAVE10")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> couponService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Coupon code already exists");

        verify(couponRepository, never()).save(any(Coupon.class));
        verifyNoInteractions(categoryEligibilityRepository, domainEventPublisher);
    }

    @Test
    void create_shouldThrowBusinessException_whenCategoryScopedCouponHasNoCategories() {
        // Given
        CreateCouponRequest request = createCouponRequest("CATEGORY10", DiscountScope.CATEGORY, Set.of());

        when(couponRepository.existsByCodeIgnoreCase("CATEGORY10")).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> couponService.create(request))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception).hasMessageContaining("Category-scoped coupon requires");
                });

        verify(couponRepository, never()).save(any(Coupon.class));
        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    void update_shouldReplaceDefinitionCategoryEligibilityStatusAndPublishEvent() {
        // Given
        UUID couponId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Coupon existing = coupon(couponId, "OLD10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");
        UpdateCouponRequest request = updateCouponRequest(" new20 ", CouponStatus.INACTIVE, DiscountScope.CATEGORY, Set.of(categoryId));

        when(couponRepository.findById(couponId)).thenReturn(Optional.of(existing));
        when(couponRepository.findByCodeIgnoreCase("NEW20")).thenReturn(Optional.empty());
        when(categoryService.requireCategory(categoryId)).thenReturn(category(categoryId));
        when(couponRepository.save(existing)).thenReturn(existing);
        when(categoryEligibilityRepository.findByCouponId(couponId)).thenReturn(List.of(categoryEligibility(categoryId)));
        when(enrollmentRepository.findByCouponId(couponId)).thenReturn(List.of());

        // When
        CouponResponse response = couponService.update(couponId, request);

        // Then
        assertThat(existing.getCode()).isEqualTo("NEW20");
        assertThat(existing.getStatus()).isEqualTo(CouponStatus.INACTIVE);
        assertThat(response.code()).isEqualTo("NEW20");
        assertThat(response.status()).isEqualTo(CouponStatus.INACTIVE);
        assertThat(response.eligibleCategoryIds()).containsExactly(categoryId);

        verify(categoryEligibilityRepository).deleteByCouponId(couponId);
        verify(categoryEligibilityRepository).flush();
        verify(domainEventPublisher).publish(eq(couponId), eq("Coupon"), eq(DomainEventType.COUPON_UPDATED), anyMap());
    }

    @Test
    void update_shouldThrowDuplicateResourceException_whenCodeBelongsToDifferentCoupon() {
        // Given
        UUID couponId = UUID.randomUUID();
        Coupon existing = coupon(couponId, "SAVE10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");
        Coupon otherCoupon = coupon(UUID.randomUUID(), "SAVE20", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "20.00");
        UpdateCouponRequest request = updateCouponRequest("SAVE20", CouponStatus.ACTIVE, DiscountScope.CART, Set.of());

        when(couponRepository.findById(couponId)).thenReturn(Optional.of(existing));
        when(couponRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(otherCoupon));

        // When / Then
        assertThatThrownBy(() -> couponService.update(couponId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Coupon code already exists");

        verify(couponRepository, never()).save(any(Coupon.class));
    }

    @Test
    void requireActiveByCode_shouldNormalizeCodeAndReturnCoupon_whenFound() {
        // Given
        Coupon coupon = coupon(UUID.randomUUID(), "SAVE10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");
        when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(coupon));

        // When
        Coupon result = couponService.requireActiveByCode(" save10 ");

        // Then
        assertThat(result).isSameAs(coupon);
        verify(couponRepository).findByCodeIgnoreCase("SAVE10");
    }

    @Test
    void requireActiveByCode_shouldThrowResourceNotFoundException_whenCouponDoesNotExist() {
        // Given
        when(couponRepository.findByCodeIgnoreCase("MISSING")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> couponService.requireActiveByCode("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Coupon not found");
    }

    @Test
    void list_shouldClampPageSizeAndMapCoupons() {
        // Given
        Coupon coupon = coupon(UUID.randomUUID(), "SAVE10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");
        when(couponRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(coupon)));
        when(categoryEligibilityRepository.findByCouponId(coupon.getId())).thenReturn(List.of());
        when(enrollmentRepository.findByCouponId(coupon.getId())).thenReturn(List.of());

        // When
        Page<CouponResponse> response = couponService.list(-1, 500);

        // Then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).code()).isEqualTo("SAVE10");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(couponRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void eligibleForProduct_shouldReturnEmptyList_whenProductIsNotPublished() {
        // Given
        UUID productId = UUID.randomUUID();
        Product product = product(productId, UUID.randomUUID(), UUID.randomUUID(), ProductStatus.DRAFT, "100.00");
        when(productService.requireProduct(productId)).thenReturn(product);

        // When
        List<EligibleCouponResponse> responses = couponService.eligibleForProduct(productId);

        // Then
        assertThat(responses).isEmpty();
        verifyNoInteractions(couponRepository, enrollmentRepository, discountStrategyFactory);
    }

    @Test
    void eligibleForProduct_shouldReturnCurrentlyUsableEnrolledCategoryEligibleCoupons() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Product product = product(productId, UUID.randomUUID(), categoryId, ProductStatus.PUBLISHED, "200.00");
        Coupon coupon = coupon(UUID.randomUUID(), "SAVE25", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "25.00");
        Coupon expired = coupon(UUID.randomUUID(), "OLD", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");
        expired.setEndsAt(Instant.now().minusSeconds(60));
        DiscountStrategy discountStrategy = mock(DiscountStrategy.class);

        when(productService.requireProduct(productId)).thenReturn(product);
        when(couponRepository.findAll()).thenReturn(List.of(coupon, expired));
        when(enrollmentRepository.existsByCouponIdAndProductId(coupon.getId(), productId)).thenReturn(true);
        when(categoryEligibilityRepository.findByCouponId(coupon.getId())).thenReturn(List.of());
        when(discountStrategyFactory.get(DiscountType.FLAT)).thenReturn(discountStrategy);
        when(discountStrategy.calculate(new BigDecimal("200.00"), coupon)).thenReturn(new BigDecimal("25.00"));

        // When
        List<EligibleCouponResponse> responses = couponService.eligibleForProduct(productId);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).code()).isEqualTo("SAVE25");
        assertThat(responses.get(0).eligible()).isTrue();
        assertThat(responses.get(0).estimatedDiscount()).isEqualByComparingTo("25.00");
    }

    @Test
    void eligibleForCart_shouldReturnEmptyList_whenProductsAreMissing() {
        // Given / When
        List<EligibleCouponResponse> responses = couponService.eligibleForCart(Map.of(), Map.of(), BigDecimal.ZERO);

        // Then
        assertThat(responses).isEmpty();
        verifyNoInteractions(couponRepository, enrollmentRepository, discountStrategyFactory);
    }

    @Test
    void eligibleForCart_shouldReturnEligibleCoupon_whenCartHasEnrolledProductAndMinimumIsMet() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Product product = product(productId, UUID.randomUUID(), categoryId, ProductStatus.PUBLISHED, "100.00");
        Coupon coupon = coupon(UUID.randomUUID(), "SAVE15", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "15.00");
        coupon.setMinCartAmount(new BigDecimal("50.00"));
        DiscountStrategy discountStrategy = mock(DiscountStrategy.class);

        when(couponRepository.findAll()).thenReturn(List.of(coupon));
        when(enrollmentRepository.existsByCouponIdAndProductId(coupon.getId(), productId)).thenReturn(true);
        when(categoryEligibilityRepository.findByCouponId(coupon.getId())).thenReturn(List.of());
        when(discountStrategyFactory.get(DiscountType.FLAT)).thenReturn(discountStrategy);
        when(discountStrategy.calculate(new BigDecimal("100.00"), coupon)).thenReturn(new BigDecimal("15.00"));

        // When
        List<EligibleCouponResponse> responses = couponService.eligibleForCart(
                Map.of(productId, product),
                Map.of(productId, new BigDecimal("100.00")),
                new BigDecimal("100.00")
        );

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).code()).isEqualTo("SAVE15");
        assertThat(responses.get(0).eligible()).isTrue();
        assertThat(responses.get(0).estimatedDiscount()).isEqualByComparingTo("15.00");
    }

    @Test
    void eligibleForCart_shouldReturnAlmostEligibleCoupon_whenMinimumCartAmountIsNotMet() {
        // Given
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        Product product = product(productId, UUID.randomUUID(), categoryId, ProductStatus.PUBLISHED, "100.00");
        Coupon coupon = coupon(UUID.randomUUID(), "SAVE50", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "50.00");
        coupon.setMinCartAmount(new BigDecimal("200.00"));

        when(couponRepository.findAll()).thenReturn(List.of(coupon));
        when(enrollmentRepository.existsByCouponIdAndProductId(coupon.getId(), productId)).thenReturn(true);
        when(categoryEligibilityRepository.findByCouponId(coupon.getId())).thenReturn(List.of());

        // When
        List<EligibleCouponResponse> responses = couponService.eligibleForCart(
                Map.of(productId, product),
                Map.of(productId, new BigDecimal("100.00")),
                new BigDecimal("100.00")
        );

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).eligible()).isFalse();
        assertThat(responses.get(0).estimatedDiscount()).isEqualByComparingTo("0.00");
        assertThat(responses.get(0).reason()).contains("Add ₹100.00 more");
        verifyNoInteractions(discountStrategyFactory);
    }

    @Test
    void enrolledProductIds_shouldReturnEmptySetAndSkipRepository_whenCandidateSetIsEmpty() {
        // Given / When
        Set<UUID> productIds = couponService.enrolledProductIds(UUID.randomUUID(), Set.of());

        // Then
        assertThat(productIds).isEmpty();
        verifyNoInteractions(enrollmentRepository);
    }

    @Test
    void isCategoryEligible_shouldReturnTrue_whenCouponHasNoCategoryRestrictions() {
        // Given
        Coupon coupon = coupon(UUID.randomUUID(), "SAVE10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");
        when(categoryEligibilityRepository.findByCouponId(coupon.getId())).thenReturn(List.of());

        // When
        boolean eligible = couponService.isCategoryEligible(coupon, UUID.randomUUID());

        // Then
        assertThat(eligible).isTrue();
        verifyNoInteractions(categoryService);
    }

    @Test
    void isCategoryEligible_shouldReturnTrue_whenProductCategoryAncestorIsEligible() {
        // Given
        UUID eligibleParentCategoryId = UUID.randomUUID();
        UUID productCategoryId = UUID.randomUUID();
        Coupon coupon = coupon(UUID.randomUUID(), "SAVE10", DiscountType.FLAT, DiscountScope.CATEGORY, CouponStatus.ACTIVE, "10.00");

        when(categoryEligibilityRepository.findByCouponId(coupon.getId())).thenReturn(List.of(categoryEligibility(eligibleParentCategoryId)));
        when(categoryService.categoryAndAncestorIds(productCategoryId)).thenReturn(Set.of(productCategoryId, eligibleParentCategoryId));

        // When
        boolean eligible = couponService.isCategoryEligible(coupon, productCategoryId);

        // Then
        assertThat(eligible).isTrue();
    }

    @Test
    void validateApplicableToCart_shouldThrowBusinessException_whenCouponIsExpired() {
        // Given
        Coupon expired = coupon(UUID.randomUUID(), "OLD10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");
        expired.setEndsAt(Instant.now().minusSeconds(60));
        when(couponRepository.findByCodeIgnoreCase("OLD10")).thenReturn(Optional.of(expired));

        // When / Then
        assertThatThrownBy(() -> couponService.validateApplicableToCart("OLD10", UUID.randomUUID()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.COUPON_NOT_APPLICABLE);
                    assertThat(exception).hasMessageContaining("Coupon has expired");
                });
    }

    @Test
    void enrollProduct_shouldSaveEnrollment_whenSellerOwnsProductAndCategoryIsEligible() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        UUID enrollmentId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product product = product(productId, sellerId, categoryId, ProductStatus.PUBLISHED, "100.00");
        Coupon coupon = coupon(couponId, "SAVE10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(coupon));
        when(productService.requireProduct(productId)).thenReturn(product);
        when(categoryEligibilityRepository.findByCouponId(couponId)).thenReturn(List.of());
        when(enrollmentRepository.findByCouponIdAndProductId(couponId, productId)).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(CouponProductEnrollment.class))).thenAnswer(invocation -> {
            CouponProductEnrollment saved = invocation.getArgument(0);
            saved.setId(enrollmentId);
            return saved;
        });

        // When
        CouponEnrollmentResponse response = couponService.enrollProduct(sellerUserId, "save10", productId);

        // Then
        assertThat(response.enrollmentId()).isEqualTo(enrollmentId);
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.sellerId()).isEqualTo(sellerId);

        ArgumentCaptor<CouponProductEnrollment> enrollment = ArgumentCaptor.forClass(CouponProductEnrollment.class);
        verify(enrollmentRepository).save(enrollment.capture());
        assertThat(enrollment.getValue().getCoupon()).isSameAs(coupon);
        assertThat(enrollment.getValue().getProductId()).isEqualTo(productId);
        assertThat(enrollment.getValue().getSellerProfile()).isSameAs(seller);
    }

    @Test
    void enrollProduct_shouldThrowForbiddenOperationException_whenProductBelongsToAnotherSeller() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID anotherSellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product product = product(productId, anotherSellerId, UUID.randomUUID(), ProductStatus.PUBLISHED, "100.00");
        Coupon coupon = coupon(couponId, "SAVE10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(coupon));
        when(productService.requireProduct(productId)).thenReturn(product);

        // When / Then
        assertThatThrownBy(() -> couponService.enrollProduct(sellerUserId, "SAVE10", productId))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("another seller product");

        verify(enrollmentRepository, never()).save(any(CouponProductEnrollment.class));
    }

    @Test
    void unenrollProduct_shouldDeleteEnrollment_whenSellerOwnsProduct() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product product = product(productId, sellerId, UUID.randomUUID(), ProductStatus.PUBLISHED, "100.00");
        Coupon coupon = coupon(couponId, "SAVE10", DiscountType.FLAT, DiscountScope.CART, CouponStatus.ACTIVE, "10.00");

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(couponRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(coupon));
        when(productService.requireProduct(productId)).thenReturn(product);

        // When
        couponService.unenrollProduct(sellerUserId, "SAVE10", productId);

        // Then
        verify(enrollmentRepository).deleteByCouponIdAndProductId(couponId, productId);
    }

    private CreateCouponRequest createCouponRequest(String code, DiscountScope scope, Set<UUID> categoryIds) {
        Instant now = Instant.now();
        return new CreateCouponRequest(
                code,
                "Test coupon",
                DiscountType.FLAT,
                scope,
                new BigDecimal("10.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                now.minusSeconds(60),
                now.plusSeconds(3600),
                categoryIds
        );
    }

    private UpdateCouponRequest updateCouponRequest(String code, CouponStatus status, DiscountScope scope, Set<UUID> categoryIds) {
        Instant now = Instant.now();
        return new UpdateCouponRequest(
                code,
                "Updated coupon",
                DiscountType.FLAT,
                scope,
                new BigDecimal("20.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                status,
                now.minusSeconds(60),
                now.plusSeconds(3600),
                categoryIds
        );
    }

    private Coupon coupon(UUID couponId, String code, DiscountType discountType, DiscountScope discountScope, CouponStatus status, String value) {
        Instant now = Instant.now();
        Coupon coupon = new Coupon();
        coupon.setId(couponId);
        coupon.setCode(code);
        coupon.setDescription("Test coupon");
        coupon.setDiscountType(discountType);
        coupon.setDiscountScope(discountScope);
        coupon.setValue(new BigDecimal(value));
        coupon.setMaxDiscountAmount(new BigDecimal(value));
        coupon.setMinCartAmount(BigDecimal.ZERO);
        coupon.setStatus(status);
        coupon.setStartsAt(now.minusSeconds(60));
        coupon.setEndsAt(now.plusSeconds(3600));
        return coupon;
    }

    private CouponCategoryEligibility categoryEligibility(UUID categoryId) {
        CouponCategoryEligibility eligibility = new CouponCategoryEligibility();
        eligibility.setId(UUID.randomUUID());
        eligibility.setCategoryId(categoryId);
        return eligibility;
    }

    private Category category(UUID categoryId) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Electronics");
        category.setSlug("electronics");
        category.setActive(true);
        return category;
    }

    private Product product(UUID productId, UUID sellerId, UUID categoryId, ProductStatus status, String price) {
        SellerProfile seller = seller(sellerId);
        Category category = category(categoryId);

        Product product = new Product();
        product.setId(productId);
        product.setSellerProfile(seller);
        product.setCategory(category);
        product.setName("Keyboard");
        product.setSku("SKU-" + productId.toString().substring(0, 8));
        product.setPrice(new BigDecimal(price));
        product.setCurrency(CurrencyCode.INR);
        product.setStatus(status);
        return product;
    }

    private SellerProfile seller(UUID sellerId) {
        SellerProfile seller = new SellerProfile();
        seller.setId(sellerId);
        seller.setBusinessName("Acme Seller");
        return seller;
    }
}
