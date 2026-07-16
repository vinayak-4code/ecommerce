package com.acme.ecommerce.ui.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit specification for Customer storefront/account page routes. */
class CustomerUiControllerTest {

    private final CustomerUiController controller = new CustomerUiController();

    @Test
    void dashboard_shouldReturnCustomerDashboardTemplate() {
        // Given
        // A customer dashboard request is routed to the UI controller.

        // When
        String viewName = controller.dashboard();

        // Then
        assertThat(viewName).isEqualTo("customer/dashboard");
    }

    @Test
    void login_shouldReturnCustomerLoginTemplate() {
        // Given
        // The customer login page is a static MVC route.

        // When
        String viewName = controller.login();

        // Then
        assertThat(viewName).isEqualTo("customer/login");
    }

    @Test
    void signup_shouldReturnCustomerSignupTemplate() {
        // Given
        // The customer signup page is a static MVC route.

        // When
        String viewName = controller.signup();

        // Then
        assertThat(viewName).isEqualTo("customer/signup");
    }
}
