package com.acme.ecommerce.seller.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.seller.dto.SellerProfileResponse;
import com.acme.ecommerce.seller.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seller profile API. Seller signup creates this profile and seller-owned
 * workflows use it to enforce product, warehouse, and inventory ownership.
 */
@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {
    private final SellerService sellerService;

    /**
     * Returns the seller profile linked to the current Bearer token.
     */
    @GetMapping("/me")
    public SellerProfileResponse me() {
        return sellerService.getMyProfile(CurrentUser.require().userId());
    }
}
