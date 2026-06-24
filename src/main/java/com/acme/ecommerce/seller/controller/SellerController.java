package com.acme.ecommerce.seller.controller;

import com.acme.ecommerce.common.security.CurrentUser;
import com.acme.ecommerce.seller.dto.SellerProfileResponse;
import com.acme.ecommerce.seller.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sellers")
@RequiredArgsConstructor
public class SellerController {
    private final SellerService sellerService;

    @GetMapping("/me")
    public SellerProfileResponse me() {
        return sellerService.getMyProfile(CurrentUser.require().userId());
    }
}
