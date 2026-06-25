package com.acme.ecommerce.coupon.service;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.service.CategoryService;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ForbiddenOperationException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.dto.CouponEnrollmentResponse;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.dto.UpdateCouponRequest;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.entity.CouponCategoryEligibility;
import com.acme.ecommerce.coupon.entity.CouponProductEnrollment;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.repository.CouponCategoryEligibilityRepository;
import com.acme.ecommerce.coupon.repository.CouponProductEnrollmentRepository;
import com.acme.ecommerce.coupon.repository.CouponRepository;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coupon command/query service.
 *
 * <p>Product admins create/update coupon definitions and category restrictions.
 * Sellers then opt in their own products. During cart pricing only enrolled and
 * category-eligible products can receive the coupon discount.</p>
 */
@Service
@RequiredArgsConstructor
public class CouponService {
    private static final String AGGREGATE_TYPE = "Coupon";

    private final CouponRepository couponRepository;
    private final CouponCategoryEligibilityRepository categoryEligibilityRepository;
    private final CouponProductEnrollmentRepository enrollmentRepository;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final SellerService sellerService;
    private final CouponValidator couponValidator;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public CouponResponse create(CreateCouponRequest request) {
        String code = normalize(request.code());
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Coupon code already exists");
        }
        Coupon coupon = new Coupon();
        applyDefinition(coupon, code, request.description(), request.discountType(), request.discountScope(), request.value(),
                request.maxDiscountAmount(), request.minCartAmount(), request.startsAt(), request.endsAt());
        validateScopeEligibility(request.discountScope(), request.categoryIds());
        Coupon saved = couponRepository.save(coupon);
        replaceCategoryEligibility(saved, request.categoryIds());
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, DomainEventType.COUPON_CREATED, Map.of("couponId", saved.getId(), "code", saved.getCode()));
        return toResponse(saved);
    }

    @Transactional
    public CouponResponse update(UUID couponId, UpdateCouponRequest request) {
        Coupon coupon = requireById(couponId);
        String code = normalize(request.code());
        couponRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(couponId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Coupon code already exists");
                });
        applyDefinition(coupon, code, request.description(), request.discountType(), request.discountScope(), request.value(),
                request.maxDiscountAmount(), request.minCartAmount(), request.startsAt(), request.endsAt());
        validateScopeEligibility(request.discountScope(), request.categoryIds());
        coupon.setStatus(request.status());
        Coupon saved = couponRepository.save(coupon);
        replaceCategoryEligibility(saved, request.categoryIds());
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, DomainEventType.COUPON_UPDATED, Map.of("couponId", saved.getId(), "code", saved.getCode()));
        return toResponse(saved);
    }

    @Transactional
    public CouponEnrollmentResponse enrollProduct(UUID sellerUserId, String couponCode, UUID productId) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Coupon coupon = requireActiveByCode(couponCode);
        Product product = productService.requireProduct(productId);
        if (!product.getSellerProfile().getId().equals(seller.getId())) {
            throw new ForbiddenOperationException("Seller cannot enroll another seller product");
        }
        if (!isCategoryEligible(coupon, product.getCategory().getId())) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Coupon is not eligible for this product category");
        }
        CouponProductEnrollment enrollment = enrollmentRepository.findByCouponIdAndProductId(coupon.getId(), productId)
                .orElseGet(CouponProductEnrollment::new);
        enrollment.setCoupon(coupon);
        enrollment.setProductId(productId);
        enrollment.setSellerProfile(seller);
        CouponProductEnrollment saved = enrollmentRepository.save(enrollment);
        return toEnrollmentResponse(saved);
    }

    @Transactional
    public void unenrollProduct(UUID sellerUserId, String couponCode, UUID productId) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Coupon coupon = requireActiveByCode(couponCode);
        Product product = productService.requireProduct(productId);
        if (!product.getSellerProfile().getId().equals(seller.getId())) {
            throw new ForbiddenOperationException("Seller cannot unenroll another seller product");
        }
        enrollmentRepository.deleteByCouponIdAndProductId(coupon.getId(), productId);
    }

    @Transactional(readOnly = true)
    public Coupon requireActiveByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(normalize(code))
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
    }

    @Transactional(readOnly = true)
    public CouponResponse getByCode(String code) {
        return toResponse(requireActiveByCode(code));
    }

    @Transactional(readOnly = true)
    public Page<CouponResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "code"));
        return couponRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Set<UUID> enrolledProductIds(UUID couponId, Set<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Set.of();
        }
        return enrollmentRepository.findEnrolledProductIds(couponId, productIds);
    }

    @Transactional(readOnly = true)
    public boolean isCategoryEligible(Coupon coupon, UUID productCategoryId) {
        Set<UUID> eligibleCategoryIds = categoryIds(coupon.getId());
        if (eligibleCategoryIds.isEmpty()) {
            return true;
        }
        Set<UUID> productCategoryAndAncestors = categoryService.categoryAndAncestorIds(productCategoryId);
        return productCategoryAndAncestors.stream().anyMatch(eligibleCategoryIds::contains);
    }

    public String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void applyDefinition(Coupon coupon, String code, String description, DiscountType discountType,
                                 DiscountScope discountScope, java.math.BigDecimal value,
                                 java.math.BigDecimal maxDiscountAmount, java.math.BigDecimal minCartAmount,
                                 java.time.Instant startsAt, java.time.Instant endsAt) {
        coupon.setCode(code);
        coupon.setDescription(description);
        coupon.setDiscountType(discountType);
        coupon.setDiscountScope(discountScope);
        coupon.setValue(MoneyUtil.money(value));
        coupon.setMaxDiscountAmount(MoneyUtil.money(maxDiscountAmount));
        coupon.setMinCartAmount(MoneyUtil.money(minCartAmount));
        coupon.setStartsAt(startsAt);
        coupon.setEndsAt(endsAt);
        couponValidator.validateDefinition(coupon);
    }

    private void validateScopeEligibility(DiscountScope discountScope, Set<UUID> categoryIds) {
        if (discountScope == DiscountScope.CATEGORY && (categoryIds == null || categoryIds.isEmpty())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Category-scoped coupon requires at least one eligible category");
        }
    }

    private void replaceCategoryEligibility(Coupon coupon, Set<UUID> categoryIds) {
        categoryEligibilityRepository.deleteByCouponId(coupon.getId());
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        Set<UUID> uniqueCategoryIds = new LinkedHashSet<>(categoryIds);
        uniqueCategoryIds.forEach(categoryService::requireCategory);
        categoryEligibilityRepository.saveAll(uniqueCategoryIds.stream()
                .map(categoryId -> toCategoryEligibility(coupon, categoryId))
                .toList());
    }

    private CouponCategoryEligibility toCategoryEligibility(Coupon coupon, UUID categoryId) {
        CouponCategoryEligibility eligibility = new CouponCategoryEligibility();
        eligibility.setCoupon(coupon);
        eligibility.setCategoryId(categoryId);
        return eligibility;
    }

    private Coupon requireById(UUID couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountScope(),
                coupon.getValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinCartAmount(),
                coupon.getStatus(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                categoryIds(coupon.getId()),
                enrolledProductIds(coupon.getId())
        );
    }

    private CouponEnrollmentResponse toEnrollmentResponse(CouponProductEnrollment enrollment) {
        return new CouponEnrollmentResponse(
                enrollment.getId(),
                enrollment.getCoupon().getId(),
                enrollment.getCoupon().getCode(),
                enrollment.getProductId(),
                enrollment.getSellerProfile().getId(),
                enrollment.getCreatedAt()
        );
    }

    private Set<UUID> categoryIds(UUID couponId) {
        return categoryEligibilityRepository.findByCouponId(couponId).stream()
                .map(CouponCategoryEligibility::getCategoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<UUID> enrolledProductIds(UUID couponId) {
        return enrollmentRepository.findByCouponId(couponId).stream()
                .map(CouponProductEnrollment::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
