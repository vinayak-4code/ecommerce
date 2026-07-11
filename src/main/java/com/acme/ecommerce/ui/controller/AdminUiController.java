package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders Product Admin pages. The browser calls protected REST APIs with JWT Bearer tokens. */
@Controller
public class AdminUiController {
    @GetMapping({"/admin", "/admin/dashboard"})
    public String dashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/login")
    public String login() {
        return "admin/login";
    }
}
