package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public storefront page controller.
 *
 * <p>The root page is intentionally public so shoppers can browse products
 * before logging in. Generic /login and /signup routes redirect to the customer
 * flow; seller/admin have their own role-specific entry points.</p>
 */
@Controller
public class DashboardController {
    /** Renders the public commerce storefront and role entry page. */
    @GetMapping({"/", "/dashboard"})
    public String dashboard() {
        return "dashboard";
    }

    /** Redirects generic signup to the customer signup journey. */
    @GetMapping("/signup")
    public String signup() {
        return "redirect:/customer/signup";
    }

    /** Redirects generic login to the customer login journey. */
    @GetMapping("/login")
    public String login() {
        return "redirect:/customer/login";
    }
}
