package com.acme.ecommerce.coupon.service;

import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.coupon.dto.CouponResponse;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CouponService {
    private final CouponRepository couponRepository;

    @Transactional
    public CouponResponse create(CreateCouponRequest request) {
        String code = normalize(request.code());
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("Coupon code already exists");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDescription(request.description());
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountScope(request.discountScope());
        coupon.setValue(MoneyUtil.money(request.value()));
        coupon.setMinCartAmount(MoneyUtil.money(request.minCartAmount()));
        coupon.setProductId(request.productId());
        coupon.setStartsAt(request.startsAt());
        coupon.setEndsAt(request.endsAt());
        return toResponse(couponRepository.save(coupon));
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

    public String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getDiscountType(),
                coupon.getDiscountScope(),
                coupon.getValue(),
                coupon.getMinCartAmount(),
                coupon.getProductId(),
                coupon.getStatus(),
                coupon.getStartsAt(),
                coupon.getEndsAt()
        );
    }
}
