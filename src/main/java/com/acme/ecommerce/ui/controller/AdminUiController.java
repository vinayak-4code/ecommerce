package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf page controller for the Product Admin journey.
 *
 * <p>The page is intentionally lightweight: it renders forms for category and
 * coupon operations, then the browser calls the protected REST APIs with the
 * Product Admin Bearer token. Example flow: open {@code /admin}, login as
 * {@code admin@example.com}, create a sub-classification, and create a coupon.</p>
 */
@Controller
public class AdminUiController {

    /**
     * Renders the Product Admin workspace under a dedicated base URL.
     *
     * @return Thymeleaf template path for the admin dashboard
     */
    @GetMapping("/admin")
    public String dashboard() {
        return "admin/dashboard";
    }
}
