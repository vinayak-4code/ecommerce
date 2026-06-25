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
        List<CartPricingResult.CartLinePrice> lines = lineDrafts.stream()
                .map(line -> toPricedLine(line, discountsByProduct.getOrDefault(line.productId(), MoneyUtil.ZERO)))
                .toList();
        boolean checkoutReady = lines.stream().allMatch(line -> line.stockStatus() == CartItemStockStatus.IN_STOCK);
        return new CartPricingResult(cart.getId(), lines, cart.getCouponCode(), checkoutReady, subtotal, totalDiscount, MoneyUtil.money(subtotal.subtract(totalDiscount)));
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
        Coupon coupon = couponService.requireActiveByCode(couponCode);
        couponValidator.validateUsableNow(coupon, subtotal);
        Set<UUID> productIds = lines.stream().map(LineDraft::productId).collect(Collectors.toSet());
        Set<UUID> enrolledProductIds = couponService.enrolledProductIds(coupon.getId(), productIds);
        List<LineDraft> eligibleLines = lines.stream()
                .filter(line -> enrolledProductIds.contains(line.productId()))
                .filter(line -> couponService.isCategoryEligible(coupon, line.categoryId()))
                .toList();
        if (eligibleLines.isEmpty()) {
            throw new BusinessException(ErrorCode.COUPON_NOT_APPLICABLE, "Coupon does not apply to any enrolled product in this cart");
        }
        BigDecimal eligibleSubtotal = MoneyUtil.money(eligibleLines.stream().map(LineDraft::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = discountStrategyFactory.get(coupon.getDiscountType()).calculate(eligibleSubtotal, coupon);
        if (discount.compareTo(BigDecimal.ZERO) == 0 || eligibleSubtotal.compareTo(BigDecimal.ZERO) == 0) {
            return zeroDiscounts;
        }
        BigDecimal allocated = MoneyUtil.ZERO;
        Map<UUID, BigDecimal> discounts = new java.util.LinkedHashMap<>(zeroDiscounts);
        for (int index = 0; index < eligibleLines.size(); index++) {
            LineDraft line = eligibleLines.get(index);
            BigDecimal lineDiscount;
            if (index == eligibleLines.size() - 1) {
                lineDiscount = MoneyUtil.money(discount.subtract(allocated));
            } else {
                lineDiscount = MoneyUtil.money(discount.multiply(line.subtotal()).divide(eligibleSubtotal, MoneyUtil.SCALE + 4, MoneyUtil.ROUNDING_MODE));
                allocated = MoneyUtil.money(allocated.add(lineDiscount));
            }
            discounts.put(line.productId(), lineDiscount);
        }
        return discounts;
    }

    private CartPricingResult.CartLinePrice toPricedLine(LineDraft line, BigDecimal discount) {
        return new CartPricingResult.CartLinePrice(
                line.productId(),
                line.productName(),
                line.quantity(),
                line.availableQuantity(),
                line.stockStatus(),
                MoneyUtil.money(line.unitPrice()),
                MoneyUtil.money(line.subtotal()),
                MoneyUtil.money(discount),
                MoneyUtil.money(line.subtotal().subtract(discount))
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
