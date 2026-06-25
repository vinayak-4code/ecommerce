package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf page controller for the Seller journey.
 *
 * <p>The page exercises seller-owned operations without introducing server-side
 * sessions. Example flow: open {@code /seller}, login as {@code seller@example.com},
 * create a warehouse, create a product, update inventory, and enroll the product
 * into an active coupon.</p>
 */
@Controller
public class SellerUiController {

    /**
     * Renders the seller workspace under a dedicated base URL.
     *
     * @return Thymeleaf template path for the seller dashboard
     */
    @GetMapping("/seller")
    public String dashboard() {
        return "seller/dashboard";
    }
}
