package com.acme.ecommerce.ui.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit specification for Product Admin UI page routes. */
class AdminUiControllerTest {

    private final AdminUiController controller = new AdminUiController();

    @Test
    void dashboard_shouldReturnAdminDashboardTemplate() {
        // Given
        // A product admin page request is routed to the controller.

        // When
        String viewName = controller.dashboard();

        // Then
        assertThat(viewName).isEqualTo("admin/dashboard");
    }

    @Test
    void login_shouldReturnAdminLoginTemplate() {
        // Given
        // Login pages are public and do not need a security context.

        // When
        String viewName = controller.login();

        // Then
        assertThat(viewName).isEqualTo("admin/login");
    }
}
