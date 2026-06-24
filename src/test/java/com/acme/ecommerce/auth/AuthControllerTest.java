package com.acme.ecommerce.auth;

import com.acme.ecommerce.auth.controller.AuthController;
import com.acme.ecommerce.auth.dto.AuthResponse;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void signupSellerReturnsBearerTokenAndSellerRole() throws Exception {
        AuthResponse response = new AuthResponse("Bearer", "token-123", Instant.now().plusSeconds(3600), UUID.randomUUID(), UserRole.SELLER);
        when(authService.signupSeller(any(SellerSignupRequest.class))).thenReturn(response);

        SellerSignupRequest request = new SellerSignupRequest("seller@example.com", "Password1", "Acme Seller", "9999999999");

        mockMvc.perform(post("/api/v1/auth/sellers/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").value("token-123"))
                .andExpect(jsonPath("$.role").value("SELLER"));
    }

    @Test
    void signupSellerRejectsInvalidPayload() throws Exception {
        SellerSignupRequest request = new SellerSignupRequest("bad-email", "short", "", null);

        mockMvc.perform(post("/api/v1/auth/sellers/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
