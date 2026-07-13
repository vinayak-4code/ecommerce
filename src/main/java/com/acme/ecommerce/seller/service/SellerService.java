package com.acme.ecommerce.seller.service;

import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.seller.dto.SellerProfileResponse;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.repository.SellerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seller profile lookup service used by seller-owned workflows.
 * Example: product and warehouse commands pass the authenticated user id here
 * to resolve seller ownership before modifying data.
 */
@Service
@RequiredArgsConstructor
public class SellerService {
    private final SellerProfileRepository sellerProfileRepository;

    /**
     * Resolves the seller profile for the authenticated seller user id.
     */
    @Transactional(readOnly = true)
    public SellerProfile requireByUserId(UUID userId) {
        return sellerProfileRepository.findByUserAccountId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found"));
    }

    /**
     * Returns the current seller profile response for UI/API callers.
     */
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
