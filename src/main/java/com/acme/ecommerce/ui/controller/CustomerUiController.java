package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders Customer storefront/account pages. Catalog browsing remains public. */
@Controller
public class CustomerUiController {
    @GetMapping({"/customer", "/customer/dashboard"})
    public String dashboard() {
        return "customer/dashboard";
    }

    @GetMapping("/customer/login")
    public String login() {
        return "customer/login";
    }

    @GetMapping("/customer/signup")
    public String signup() {
        return "customer/signup";
    }
}
