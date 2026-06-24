package com.acme.ecommerce.cart.service;

import com.acme.ecommerce.cart.entity.Cart;
import com.acme.ecommerce.cart.entity.CartItem;
import com.acme.ecommerce.cart.repository.CartItemRepository;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.service.CouponService;
import com.acme.ecommerce.coupon.strategy.DiscountStrategyFactory;
import com.acme.ecommerce.coupon.validation.CouponValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartPricingService {
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final CouponService couponService;
    private final DiscountStrategyFactory discountStrategyFactory;
    private final CouponValidator couponValidator;

    @Transactional(readOnly = true)
    public CartPricingResult price(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        if (items.isEmpty()) {
            return new CartPricingResult(cart.getId(), List.of(), cart.getCouponCode(), MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
        }
        Map<UUID, Product> products = loadProducts(items);
        List<LineDraft> lineDrafts = items.stream().map(item -> toLineDraft(item, products.get(item.getProductId()))).toList();
        BigDecimal subtotal = MoneyUtil.money(lineDrafts.stream().map(LineDraft::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        Map<UUID, BigDecimal> discountsByProduct = allocateDiscount(cart.getCouponCode(), lineDrafts, subtotal);
        BigDecimal totalDiscount = MoneyUtil.money(discountsByProduct.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        List<CartPricingResult.CartLinePrice> lines = lineDrafts.stream()
                .map(line -> toPricedLine(line, discountsByProduct.getOrDefault(line.productId(), MoneyUtil.ZERO)))
                .toList();
        return new CartPricingResult(cart.getId(), lines, cart.getCouponCode(), subtotal, totalDiscount, MoneyUtil.money(subtotal.subtract(totalDiscount)));
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
        return new LineDraft(item.getProductId(), product.getName(), item.getQuantity(), product.getPrice(), subtotal);
    }

    private Map<UUID, BigDecimal> allocateDiscount(String couponCode, List<LineDraft> lines, BigDecimal subtotal) {
        if (couponCode == null || couponCode.isBlank()) {
            return lines.stream().collect(Collectors.toMap(LineDraft::productId, ignored -> MoneyUtil.ZERO));
        }
        Coupon coupon = couponService.requireActiveByCode(couponCode);
        Set<UUID> productIds = lines.stream().map(LineDraft::productId).collect(Collectors.toSet());
        couponValidator.validate(coupon, subtotal, productIds);
        List<LineDraft> eligibleLines = coupon.getDiscountScope() == DiscountScope.CART
                ? lines
                : lines.stream().filter(line -> line.productId().equals(coupon.getProductId())).toList();
        BigDecimal eligibleSubtotal = MoneyUtil.money(eligibleLines.stream().map(LineDraft::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal discount = discountStrategyFactory.get(coupon.getDiscountType()).calculate(eligibleSubtotal, coupon);
        Map<UUID, BigDecimal> discounts = lines.stream().collect(Collectors.toMap(LineDraft::productId, ignored -> MoneyUtil.ZERO));
        if (discount.compareTo(BigDecimal.ZERO) == 0 || eligibleSubtotal.compareTo(BigDecimal.ZERO) == 0) {
            return discounts;
        }
        BigDecimal allocated = MoneyUtil.ZERO;
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
                MoneyUtil.money(line.unitPrice()),
                MoneyUtil.money(line.subtotal()),
                MoneyUtil.money(discount),
                MoneyUtil.money(line.subtotal().subtract(discount))
        );
    }

    private record LineDraft(UUID productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
    }
}
