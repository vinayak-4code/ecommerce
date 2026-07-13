package com.acme.ecommerce.coupon.service;

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
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.dto.CouponEnrollmentResponse;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.EligibleCouponResponse;
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
import com.acme.ecommerce.coupon.strategy.DiscountStrategyFactory;
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

import java.math.BigDecimal;
import java.time.Instant;
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
/**
 * Product Admin coupon service plus seller product enrollment support.
 *
 * <p>Coupons can be active for a time window, limited to categories, and linked
 * to seller-enrolled products. Multiple coupons may include the same product,
 * but CartService allows only one coupon code per cart.</p>
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
    private final DiscountStrategyFactory discountStrategyFactory;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * Creates a coupon with date window, discount settings, and category eligibility.
     */
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

    /**
     * Replaces coupon configuration and category eligibility in one transaction.
     */
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

    /**
     * Enrolls a seller-owned product into a coupon if category rules allow it.
     */
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

    /**
     * Removes a seller-owned product from a coupon enrollment.
     */
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

    /**
     * Loads an active coupon by normalized code.
     */
    @Transactional(readOnly = true)
    public Coupon requireActiveByCode(String code) {
        return couponRepository.findByCodeIgnoreCase(normalize(code))
                .orElseThrow(() -> new ResourceNotFoundException("Coupon not found"));
    }

    /**
     * Returns a coupon read model by code.
     */
    @Transactional(readOnly = true)
    public CouponResponse getByCode(String code) {
        return toResponse(requireActiveByCode(code));
    }

    /**
     * Lists coupons with bounded pagination.
     */
    @Transactional(readOnly = true)
    public Page<CouponResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "code"));
        return couponRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Returns currently usable coupons for a public product detail page.
     */
    @Transactional(readOnly = true)
    public java.util.List<EligibleCouponResponse> eligibleForProduct(UUID productId) {
        Product product = productService.requireProduct(productId);
        if (product.getStatus() != ProductStatus.PUBLISHED) {
            return java.util.List.of();
        }
        BigDecimal productSubtotal = MoneyUtil.money(product.getPrice());
        return couponRepository.findAll().stream()
                .filter(coupon -> isCurrentlyUsable(coupon, productSubtotal))
                .filter(coupon -> enrollmentRepository.existsByCouponIdAndProductId(coupon.getId(), productId))
                .filter(coupon -> isCategoryEligible(coupon, product.getCategory().getId()))
                .map(coupon -> toEligibleResponse(coupon, productSubtotal))
                .toList();
    }

    /**
     * Returns coupons applicable to the current cart lines with estimated savings.
     * Also includes "almost eligible" coupons where the minimum cart amount isn't met,
     * so the UI can show what's needed to unlock them.
     */
    @Transactional(readOnly = true)
    public java.util.List<EligibleCouponResponse> eligibleForCart(Map<UUID, Product> productsById, Map<UUID, BigDecimal> subtotalByProductId, BigDecimal cartSubtotal) {
        if (productsById == null || productsById.isEmpty()) {
            return java.util.List.of();
        }
        Instant now = Instant.now();
        return couponRepository.findAll().stream()
                .filter(coupon -> coupon.getStatus() == com.acme.ecommerce.coupon.enums.CouponStatus.ACTIVE
                        && !coupon.getStartsAt().isAfter(now)
                        && !coupon.getEndsAt().isBefore(now))
                .map(coupon -> {
                    BigDecimal eligibleSubtotal = subtotalByProductId.entrySet().stream()
                            .filter(entry -> enrollmentRepository.existsByCouponIdAndProductId(coupon.getId(), entry.getKey()))
                            .filter(entry -> {
                                Product product = productsById.get(entry.getKey());
                                return product != null && isCategoryEligible(coupon, product.getCategory().getId());
                            })
                            .map(Map.Entry::getValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (eligibleSubtotal.compareTo(BigDecimal.ZERO) <= 0) {
                        return null; // No enrolled products in cart for this coupon
                    }
                    // Check if min cart amount is met
                    boolean meetsMinCart = coupon.getMinCartAmount() == null
                            || cartSubtotal.compareTo(coupon.getMinCartAmount()) >= 0;
                    if (meetsMinCart) {
                        return toEligibleResponse(coupon, eligibleSubtotal);
                    } else {
                        // Return as "not yet eligible" with reason
                        BigDecimal shortfall = MoneyUtil.money(coupon.getMinCartAmount().subtract(cartSubtotal));
                        return new EligibleCouponResponse(
                                coupon.getId(),
                                coupon.getCode(),
                                coupon.getDescription(),
                                coupon.getDiscountType(),
                                coupon.getValue(),
                                coupon.getMaxDiscountAmount(),
                                coupon.getMinCartAmount(),
                                coupon.getStartsAt(),
                                coupon.getEndsAt(),
                                lifecycleStatus(coupon),
                                BigDecimal.ZERO,
                                false,
                                "Add ₹" + shortfall.toPlainString() + " more to unlock this coupon"
                        );
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Filters candidate product IDs down to products enrolled in the coupon.
     */
    @Transactional(readOnly = true)
    public Set<UUID> enrolledProductIds(UUID couponId, Set<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Set.of();
        }
        return enrollmentRepository.findEnrolledProductIds(couponId, productIds);
    }

    /**
     * Checks direct category and ancestor category eligibility for a coupon.
     */
    @Transactional(readOnly = true)
    public boolean isCategoryEligible(Coupon coupon, UUID productCategoryId) {
        Set<UUID> eligibleCategoryIds = categoryIds(coupon.getId());
        if (eligibleCategoryIds.isEmpty()) {
            return true;
        }
        Set<UUID> productCategoryAndAncestors = categoryService.categoryAndAncestorIds(productCategoryId);
        return productCategoryAndAncestors.stream().anyMatch(eligibleCategoryIds::contains);
    }

    /**
     * Normalizes coupon codes to a consistent uppercase format.
     */
    public String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Validates that a coupon is currently applicable before saving to cart.
     * This provides explicit error messages when a customer tries to apply an invalid coupon.
     */
    @Transactional(readOnly = true)
    public void validateApplicableToCart(String couponCode, UUID cartId) {
        Coupon coupon = requireActiveByCode(couponCode);
        Instant now = Instant.now();
        if (coupon.getStatus() != com.acme.ecommerce.coupon.enums.CouponStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Coupon is not active");
        }
        if (coupon.getStartsAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Coupon is not live yet");
        }
        if (coupon.getEndsAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Coupon has expired");
        }
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
        categoryEligibilityRepository.flush();
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

    private boolean isCurrentlyUsable(Coupon coupon, BigDecimal subtotal) {
        Instant now = Instant.now();
        return coupon.getStatus() == com.acme.ecommerce.coupon.enums.CouponStatus.ACTIVE
                && !coupon.getStartsAt().isAfter(now)
                && !coupon.getEndsAt().isBefore(now)
                && (coupon.getMinCartAmount() == null || subtotal.compareTo(coupon.getMinCartAmount()) >= 0);
    }

    private EligibleCouponResponse toEligibleResponse(Coupon coupon, BigDecimal eligibleSubtotal) {
        BigDecimal estimated = discountStrategyFactory.get(coupon.getDiscountType()).calculate(MoneyUtil.money(eligibleSubtotal), coupon);
        return new EligibleCouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getValue(),
                coupon.getMaxDiscountAmount(),
                coupon.getMinCartAmount(),
                coupon.getStartsAt(),
                coupon.getEndsAt(),
                lifecycleStatus(coupon),
                estimated
        );
    }

    private String lifecycleStatus(Coupon coupon) {
        Instant now = Instant.now();
        if (coupon.getStatus() != com.acme.ecommerce.coupon.enums.CouponStatus.ACTIVE) {
            return "INACTIVE";
        }
        if (coupon.getStartsAt().isAfter(now)) {
            return "SCHEDULED";
        }
        if (coupon.getEndsAt().isBefore(now)) {
            return "EXPIRED";
        }
        return "LIVE";
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
