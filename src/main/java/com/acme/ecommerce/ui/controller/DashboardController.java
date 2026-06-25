package com.acme.ecommerce.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tiny Thymeleaf landing page for reviewers.
 * It is intentionally read-only and points reviewers to the seeded users and curl file.
 */
@Controller
public class DashboardController {
    @GetMapping({"/", "/dashboard"})
    public String dashboard() {
        return "dashboard";
    }
}
