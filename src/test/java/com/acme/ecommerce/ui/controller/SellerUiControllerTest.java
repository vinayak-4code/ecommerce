package com.acme.ecommerce.ui.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit specification for Seller Center page routes. */
class SellerUiControllerTest {

    private final SellerUiController controller = new SellerUiController();

    @Test
    void dashboard_shouldReturnSellerDashboardTemplate() {
        // Given
        // A seller dashboard request is routed to the UI controller.

        // When
        String viewName = controller.dashboard();

        // Then
        assertThat(viewName).isEqualTo("seller/dashboard");
    }

    @Test
    void login_shouldReturnSellerLoginTemplate() {
        // Given
        // Seller login has a role-specific page.

        // When
        String viewName = controller.login();

        // Then
        assertThat(viewName).isEqualTo("seller/login");
    }

    @Test
    void signup_shouldReturnSellerSignupTemplate() {
        // Given
        // Seller signup has a role-specific page.

        // When
        String viewName = controller.signup();

        // Then
        assertThat(viewName).isEqualTo("seller/signup");
    }
}
