package com.acme.ecommerce.seller;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.config.TestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("SellerController – Functional Tests")
@Transactional
class SellerControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/sellers";

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        loginAsSeller();
    }

    @Test @DisplayName("success – GET /sellers/me returns seller profile")
    void getProfile_success() throws Exception {
        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Acme Test Store"))
                .andExpect(jsonPath("$.email").value(SELLER_EMAIL));
    }

    @Test @DisplayName("failure – no authenticated user → 500 (IllegalStateException from CurrentUser)")
    void getProfile_noAuth() throws Exception {
        TestSecurityConfig.clearSecurityContext();
        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().is5xxServerError());
    }
}

