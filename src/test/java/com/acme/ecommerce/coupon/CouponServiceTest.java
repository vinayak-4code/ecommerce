package com.acme.ecommerce.coupon;

import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.service.CategoryService;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.entity.CouponCategoryEligibility;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.repository.CouponCategoryEligibilityRepository;
import com.acme.ecommerce.coupon.repository.CouponProductEnrollmentRepository;
import com.acme.ecommerce.coupon.repository.CouponRepository;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.service.SellerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private DomainEventPublisher domainEventPublisher;

    @Test
    void productAdminCreateCouponNormalizesCodeAndStoresDefinition() {
        CouponService couponService = service();
        UUID couponId = UUID.randomUUID();
        UUID electronicsCategoryId = UUID.randomUUID();
        Category electronics = new Category();
        electronics.setId(electronicsCategoryId);
        CouponCategoryEligibility storedEligibility = new CouponCategoryEligibility();
        storedEligibility.setCategoryId(electronicsCategoryId);
        when(couponRepository.existsByCodeIgnoreCase("ELECTRO10")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenAnswer(invocation -> {
            Coupon coupon = invocation.getArgument(0);
            coupon.setId(couponId);
            storedEligibility.setCoupon(coupon);
            return coupon;
        });
        when(categoryService.requireCategory(electronicsCategoryId)).thenReturn(electronics);
        when(categoryEligibilityRepository.findByCouponId(couponId)).thenReturn(List.of(storedEligibility));
        when(enrollmentRepository.findByCouponId(couponId)).thenReturn(List.of());

        CouponResponse response = couponService.create(new CreateCouponRequest(
                " electro10 ",
                "10 percent off electronics",
                DiscountType.UPTO_PERCENT_OFF,
                DiscountScope.CATEGORY,
                new BigDecimal("10"),
                new BigDecimal("500"),
                new BigDecimal("1000"),
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2099-12-31T23:59:59Z"),
                Set.of(electronicsCategoryId)
        ));

        assertThat(response.code()).isEqualTo("ELECTRO10");
        assertThat(response.eligibleCategoryIds()).containsExactly(electronicsCategoryId);
        assertThat(response.discountType()).isEqualTo(DiscountType.UPTO_PERCENT_OFF);
        verify(domainEventPublisher).publish(any(), any(), any(), any());
    }

    @Test
    void sellerCannotEnrollProductWhenCouponCategoryDoesNotMatch() {
        CouponService couponService = service();
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID couponId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID electronicsCategoryId = UUID.randomUUID();
        UUID apparelCategoryId = UUID.randomUUID();

        SellerProfile seller = new SellerProfile();
        seller.setId(sellerId);
        Category apparel = new Category();
        apparel.setId(apparelCategoryId);
        Product product = new Product();
        product.setId(productId);
        product.setSellerProfile(seller);
        product.setCategory(apparel);
        Coupon coupon = new Coupon();
        coupon.setId(couponId);
        coupon.setCode("ELECTRO10");
        coupon.setDiscountType(DiscountType.UPTO_PERCENT_OFF);
        coupon.setDiscountScope(DiscountScope.CATEGORY);
        coupon.setValue(new BigDecimal("10"));
        CouponCategoryEligibility eligibility = new CouponCategoryEligibility();
        eligibility.setCoupon(coupon);
        eligibility.setCategoryId(electronicsCategoryId);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(couponRepository.findByCodeIgnoreCase("ELECTRO10")).thenReturn(Optional.of(coupon));
        when(productService.requireProduct(productId)).thenReturn(product);
        when(categoryEligibilityRepository.findByCouponId(couponId)).thenReturn(List.of(eligibility));
        when(categoryService.categoryAndAncestorIds(apparelCategoryId)).thenReturn(Set.of(apparelCategoryId));

        assertThatThrownBy(() -> couponService.enrollProduct(sellerUserId, "ELECTRO10", productId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible");
        verify(enrollmentRepository, never()).save(any());
    }

    private CouponService service() {
        return new CouponService(
                couponRepository,
                categoryEligibilityRepository,
                enrollmentRepository,
                productService,
                categoryService,
                sellerService,
                couponValidator,
                domainEventPublisher
        );
    }
}
