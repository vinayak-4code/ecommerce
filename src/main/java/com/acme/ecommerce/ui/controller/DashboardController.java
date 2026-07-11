package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf page controller for landing, signup, and login pages.
 */
@Controller
public class DashboardController {
    /**
     * Renders the entry page linking to Product Admin, Seller, and Customer UI journeys.
     */
    @GetMapping({"/", "/dashboard"})
    public String dashboard() {
        return "dashboard";
    }

    /**
     * Renders the signup page for Customer and Seller registration.
     */
    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    /**
     * Renders the login page.
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
