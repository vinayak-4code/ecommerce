package com.acme.ecommerce.ui.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit specification for public storefront page routing. */
class DashboardControllerTest {

    private final DashboardController controller = new DashboardController();

    @Test
    void dashboard_shouldReturnPublicDashboardTemplate() {
        // Given
        // The storefront dashboard is public.

        // When
        String viewName = controller.dashboard();

        // Then
        assertThat(viewName).isEqualTo("dashboard");
    }

    @Test
    void signup_shouldRedirectGenericSignupToCustomerSignup() {
        // Given
        // Generic signup is intentionally customer-first.

        // When
        String viewName = controller.signup();

        // Then
        assertThat(viewName).isEqualTo("redirect:/customer/signup");
    }

    @Test
    void login_shouldRedirectGenericLoginToCustomerLogin() {
        // Given
        // Generic login is intentionally customer-first.

        // When
        String viewName = controller.login();

        // Then
        assertThat(viewName).isEqualTo("redirect:/customer/login");
    }
}
