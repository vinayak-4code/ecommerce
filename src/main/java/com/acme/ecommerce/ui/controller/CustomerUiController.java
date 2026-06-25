package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf page controller for the Customer shopping journey.
 *
 * <p>The page demonstrates the most important evaluation path: product search,
 * cart mutation, one-coupon application, live cart price/inventory validation,
 * and order placement. Example flow: open {@code /customer}, login as
 * {@code customer@example.com}, add the seeded phone, apply {@code ELECTRO10},
 * view the re-priced cart, and place an order.</p>
 */
@Controller
public class CustomerUiController {

    /**
     * Renders the customer workspace under a dedicated base URL.
     *
     * @return Thymeleaf template path for the customer dashboard
     */
    @GetMapping("/customer")
    public String dashboard() {
        return "customer/dashboard";
    }
}
