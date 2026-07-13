package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.enums.CartItemStockStatus;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.coupon.strategy.DiscountStrategyFactory;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import com.acme.ecommerce.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Calculates cart totals from live product price and live inventory.
 *
 * <p>The cart stores only product IDs and quantities. On every view/checkout the
 * service reloads product price, publication status, available quantity, and the
 * current coupon configuration. This avoids stale cart totals.</p>
 */
@Service
@RequiredArgsConstructor
public class CartPricingService {
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CouponService couponService;
    private final DiscountStrategyFactory discountStrategyFactory;
    private final CouponValidator couponValidator;
    private final InventoryService inventoryService;

    /**
     * Calculates line totals, stock status, proportional discount allocation, and checkout readiness.
     */
    @Transactional(readOnly = true)
    public CartPricingResult price(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            return new CartPricingResult(cart.getId(), List.of(), cart.getCouponCode(), false, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
        }
        Map<UUID, Product> products = loadProducts(items);
        List<LineDraft> lineDrafts = items.stream().map(item -> toLineDraft(item, products.get(item.getProductId()))).toList();
        BigDecimal subtotal = MoneyUtil.money(lineDrafts.stream().map(LineDraft::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        Map<UUID, BigDecimal> discountsByProduct = allocateDiscount(cart.getCouponCode(), lineDrafts, subtotal);
        BigDecimal totalDiscount = MoneyUtil.money(discountsByProduct.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        // Defense-in-depth: total discount must never exceed subtotal
        totalDiscount = MoneyUtil.min(totalDiscount, subtotal);
        List<CartPricingResult.CartLinePrice> lines = lineDrafts.stream()
                .map(line -> toPricedLine(line, discountsByProduct.getOrDefault(line.productId(), MoneyUtil.ZERO)))
                .toList();
        boolean checkoutReady = lines.stream().allMatch(line -> line.stockStatus() == CartItemStockStatus.IN_STOCK);
        BigDecimal totalAmount = MoneyUtil.money(subtotal.subtract(totalDiscount));
        // Guard: cart total must never go below zero
        totalAmount = totalAmount.max(BigDecimal.ZERO);
        return new CartPricingResult(cart.getId(), lines, cart.getCouponCode(), checkoutReady, subtotal, totalDiscount, totalAmount);
    }

    private Map<UUID, Product> loadProducts(List<CartItem> items) {
        Set<UUID> productIds = items.stream().map(CartItem::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> products = productRepository.findByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (UUID productId : productIds) {
            Product product = products.get(productId);
            if (product == null || product.getStatus() != ProductStatus.PUBLISHED) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Cart contains unavailable product: " + productId);
            }
        }
        return products;
    }

    private LineDraft toLineDraft(CartItem item, Product product) {
        BigDecimal subtotal = MoneyUtil.multiply(product.getPrice(), item.getQuantity());
        long availableQuantity = inventoryService.consolidatedAvailable(product.getId());
        CartItemStockStatus stockStatus = resolveStockStatus(item.getQuantity(), availableQuantity);
        return new LineDraft(item.getProductId(), product.getName(), product.getCategory().getId(), item.getQuantity(), availableQuantity, stockStatus, product.getPrice(), subtotal);
    }

    private CartItemStockStatus resolveStockStatus(int requestedQuantity, long availableQuantity) {
        if (availableQuantity == 0) {
            return CartItemStockStatus.OUT_OF_STOCK;
        }
        if (requestedQuantity > availableQuantity) {
            return CartItemStockStatus.INSUFFICIENT_STOCK;
        }
        return CartItemStockStatus.IN_STOCK;
    }

    private Map<UUID, BigDecimal> allocateDiscount(String couponCode, List<LineDraft> lines, BigDecimal subtotal) {
        Map<UUID, BigDecimal> zeroDiscounts = lines.stream().collect(Collectors.toMap(LineDraft::productId, ignored -> MoneyUtil.ZERO));
        if (couponCode == null || couponCode.isBlank()) {
            return zeroDiscounts;
        }

        Coupon coupon;
        try {
            coupon = couponService.requireActiveByCode(couponCode);
            couponValidator.validateUsableNow(coupon, subtotal);
        } catch (BusinessException e) {
            // Coupon became invalid (expired, deactivated, or subtotal dropped below minimum).
            // Return zero discounts gracefully so the cart remains viewable.
            return zeroDiscounts;
        }

        Set<UUID> productIds = lines.stream().map(LineDraft::productId).collect(Collectors.toSet());
        Set<UUID> enrolledProductIds = couponService.enrolledProductIds(coupon.getId(), productIds);
        List<LineDraft> eligibleLines = lines.stream()
                .filter(line -> enrolledProductIds.contains(line.productId()))
                .filter(line -> couponService.isCategoryEligible(coupon, line.categoryId()))
                .toList();
        if (eligibleLines.isEmpty()) {
            // No products are eligible — return zero discount instead of blocking cart view
            return zeroDiscounts;
        }
        BigDecimal eligibleSubtotal = MoneyUtil.money(eligibleLines.stream().map(LineDraft::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = discountStrategyFactory.get(coupon.getDiscountType()).calculate(eligibleSubtotal, coupon);
        // Guard: discount must never exceed eligible subtotal
        discount = MoneyUtil.min(discount, eligibleSubtotal);
        if (discount.compareTo(BigDecimal.ZERO) <= 0 || eligibleSubtotal.compareTo(BigDecimal.ZERO) == 0) {
            return zeroDiscounts;
        }
        BigDecimal allocated = MoneyUtil.ZERO;
        Map<UUID, BigDecimal> discounts = new java.util.LinkedHashMap<>(zeroDiscounts);
        for (int index = 0; index < eligibleLines.size(); index++) {
            LineDraft line = eligibleLines.get(index);
            BigDecimal lineDiscount;
            if (index == eligibleLines.size() - 1) {
                // Last line gets the remainder; clamp to [0, lineSubtotal] to prevent rounding artifacts
                lineDiscount = MoneyUtil.money(discount.subtract(allocated));
                lineDiscount = lineDiscount.max(BigDecimal.ZERO);
                lineDiscount = MoneyUtil.min(lineDiscount, line.subtotal());
            } else {
                lineDiscount = MoneyUtil.money(discount.multiply(line.subtotal()).divide(eligibleSubtotal, MoneyUtil.SCALE + 4, MoneyUtil.ROUNDING_MODE));
                // Clamp: individual line discount must not exceed line subtotal
                lineDiscount = MoneyUtil.min(lineDiscount, line.subtotal());
                allocated = MoneyUtil.money(allocated.add(lineDiscount));
                // If rounding caused allocated to reach or exceed total discount, stop allocating
                if (allocated.compareTo(discount) >= 0) {
                    lineDiscount = MoneyUtil.money(lineDiscount.subtract(allocated.subtract(discount)));
                    lineDiscount = lineDiscount.max(BigDecimal.ZERO);
                    allocated = discount;
                }
            }
            discounts.put(line.productId(), lineDiscount);
        }
        return discounts;
    }

    private CartPricingResult.CartLinePrice toPricedLine(LineDraft line, BigDecimal discount) {
        // Guard: line discount must not exceed line subtotal; line total must not go negative
        BigDecimal safeDiscount = MoneyUtil.min(discount.max(BigDecimal.ZERO), line.subtotal());
        BigDecimal lineTotal = MoneyUtil.money(line.subtotal().subtract(safeDiscount)).max(BigDecimal.ZERO);
        return new CartPricingResult.CartLinePrice(
                line.productId(),
                line.productName(),
                line.quantity(),
                line.availableQuantity(),
                line.stockStatus(),
                MoneyUtil.money(line.unitPrice()),
                MoneyUtil.money(line.subtotal()),
                MoneyUtil.money(safeDiscount),
                lineTotal
        );
    }

    private record LineDraft(
            UUID productId,
            String productName,
            UUID categoryId,
            int quantity,
            long availableQuantity,
            CartItemStockStatus stockStatus,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {
    }
}
