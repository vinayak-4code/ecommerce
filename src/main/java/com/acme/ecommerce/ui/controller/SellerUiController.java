package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders Seller Center pages backed by seller-owned REST APIs. */
@Controller
public class SellerUiController {
    @GetMapping({"/seller", "/seller/dashboard"})
    public String dashboard() {
        return "seller/dashboard";
    }

    @GetMapping("/seller/login")
    public String login() {
        return "seller/login";
    }

    @GetMapping("/seller/signup")
    public String signup() {
        return "seller/signup";
    }
}
