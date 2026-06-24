package com.acme.ecommerce.seller.service;

import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.seller.dto.SellerProfileResponse;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.repository.SellerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerService {
    private final SellerProfileRepository sellerProfileRepository;

    @Transactional(readOnly = true)
    public SellerProfile requireByUserId(UUID userId) {
        return sellerProfileRepository.findByUserAccountId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found"));
    }

    @Transactional(readOnly = true)
    public SellerProfileResponse getMyProfile(UUID userId) {
        SellerProfile seller = requireByUserId(userId);
        return new SellerProfileResponse(
                seller.getId(),
                seller.getUserAccount().getEmail(),
                seller.getBusinessName(),
                seller.getContactNumber()
        );
    }
}
