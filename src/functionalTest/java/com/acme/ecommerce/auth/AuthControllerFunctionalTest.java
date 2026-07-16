package com.acme.ecommerce.auth;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.auth.dto.CustomerSignupRequest;
import com.acme.ecommerce.auth.dto.LoginRequest;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.seller.entity.SellerProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AuthController – Functional Tests")
@Transactional
class AuthControllerFunctionalTest extends BaseFunctionalTest {

    private static final String AUTH = "/api/v1/auth";

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    /** Seeds a customer account + profile directly in the DB (no API call). */
    private UserAccount seedCustomerDirectly(String email) {
        UserAccount ua = seedUserAccount(email, UserRole.CUSTOMER);
        CustomerProfile cp = new CustomerProfile();
        cp.setUserAccount(ua);
        cp.setFullName("Test User");
        cp.setPhoneNumber("1234567890");
        customerProfileRepository.save(cp);
        return ua;
    }

    /** Seeds a seller account + profile directly in the DB (no API call). */
    private UserAccount seedSellerDirectly(String email) {
        UserAccount ua = seedUserAccount(email, UserRole.SELLER);
        SellerProfile sp = new SellerProfile();
        sp.setUserAccount(ua);
        sp.setBusinessName("Acme Store");
        sp.setContactNumber("9876543210");
        sellerProfileRepository.save(sp);
        return ua;
    }

    @Nested @DisplayName("POST /sellers/signup")
    class SellerSignup {
        @Test @DisplayName("success – returns 201 with Bearer token")
        void success() throws Exception {
            mockMvc.perform(post(AUTH + "/sellers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SellerSignupRequest("new-seller@test.com", "Password1", "Acme", "555"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.role").value("SELLER"));
        }

        @Test @DisplayName("failure – duplicate email → 409")
        void duplicateEmail() throws Exception {
            seedSellerDirectly("dup-s@test.com");
            mockMvc.perform(post(AUTH + "/sellers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SellerSignupRequest("dup-s@test.com", "Password1", "X", null))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
        }

        @Test @DisplayName("validation – blank email → 400")
        void blankEmail() throws Exception {
            mockMvc.perform(post(AUTH + "/sellers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SellerSignupRequest("", "Password1", "X", null))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – short password → 400")
        void shortPassword() throws Exception {
            mockMvc.perform(post(AUTH + "/sellers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SellerSignupRequest("sp@t.com", "short", "X", null))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – blank businessName → 400")
        void blankBusinessName() throws Exception {
            mockMvc.perform(post(AUTH + "/sellers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new SellerSignupRequest("bn@t.com", "Password1", "", null))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /customers/signup")
    class CustomerSignup {
        @Test @DisplayName("success – returns 201 with CUSTOMER role")
        void success() throws Exception {
            mockMvc.perform(post(AUTH + "/customers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CustomerSignupRequest("c@test.com", "Password1", "Jane", "555"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.role").value("CUSTOMER"));
        }

        @Test @DisplayName("failure – duplicate email → 409")
        void duplicateEmail() throws Exception {
            seedCustomerDirectly("dup-c@test.com");
            mockMvc.perform(post(AUTH + "/customers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CustomerSignupRequest("dup-c@test.com", "Password1", "Dup", null))))
                    .andExpect(status().isConflict());
        }

        @Test @DisplayName("validation – blank fullName → 400")
        void blankFullName() throws Exception {
            mockMvc.perform(post(AUTH + "/customers/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CustomerSignupRequest("fn@t.com", "Password1", "", null))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /login")
    class Login {
        @Test @DisplayName("success – valid credentials return token")
        void success() throws Exception {
            seedCustomerDirectly("login@test.com");
            mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("login@test.com", "Password1", UserRole.CUSTOMER))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
        }

        @Test @DisplayName("failure – wrong password → 401")
        void wrongPassword() throws Exception {
            seedCustomerDirectly("wp@test.com");
            mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("wp@test.com", "Wrong12345", UserRole.CUSTOMER))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        }

        @Test @DisplayName("failure – role mismatch → 401")
        void roleMismatch() throws Exception {
            seedCustomerDirectly("rm@test.com");
            mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("rm@test.com", "Password1", UserRole.SELLER))))
                    .andExpect(status().isUnauthorized());
        }

        @Test @DisplayName("failure – unknown email → 401")
        void unknownEmail() throws Exception {
            mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("ghost@test.com", "Password1", UserRole.CUSTOMER))))
                    .andExpect(status().isUnauthorized());
        }

        @Test @DisplayName("validation – null role → 400")
        void nullRole() throws Exception {
            mockMvc.perform(post(AUTH + "/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"a@b.com\",\"password\":\"Password1\",\"role\":null}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("POST /logout")
    class Logout {
        @Test @DisplayName("success – no header → 204")
        void noHeader() throws Exception {
            mockMvc.perform(post(AUTH + "/logout")).andExpect(status().isNoContent());
        }

        @Test @DisplayName("success – with header → 204")
        void withHeader() throws Exception {
            mockMvc.perform(post(AUTH + "/logout").header("Authorization", "Bearer tok"))
                    .andExpect(status().isNoContent());
        }
    }
}
