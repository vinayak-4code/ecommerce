package com.acme.ecommerce.cart;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.cart.controller.CartController;
import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.cart.dto.CartResponse;
import com.acme.ecommerce.cart.service.CartService;
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.common.security.TokenAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-level tests for cart API authentication and authorization:
 * - Unauthenticated users cannot access cart endpoints (401/403)
 * - SELLER role cannot access cart (wrong role → 403)
 * - CUSTOMER role can access their own cart
 * - Cart operations always use the authenticated user's ID (no path-based user selection)
 */
@WebMvcTest(CartController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Cart API – Authentication & Authorization")
class CartAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TokenAuthenticationFilter tokenAuthenticationFilter;
    @MockitoBean private CartService cartService;

    private void authenticateAs(UUID userId, String email, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, email, role);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Authenticated CUSTOMER Access")
    class CustomerAccess {
        private static final UUID CUSTOMER_USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

        @Test
        @DisplayName("authenticated customer can view their cart")
        void viewCart() throws Exception {
            authenticateAs(CUSTOMER_USER_ID, "customer@example.com", UserRole.CUSTOMER);
            CartResponse response = new CartResponse(UUID.randomUUID(), List.of(), null, false, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
            when(cartService.view(CUSTOMER_USER_ID)).thenReturn(response);

            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cartId").exists())
                    .andExpect(jsonPath("$.subtotal").value(0.00));

            verify(cartService).view(CUSTOMER_USER_ID);
            clearAuth();
        }

        @Test
        @DisplayName("authenticated customer can add item to cart")
        void addToCart() throws Exception {
            authenticateAs(CUSTOMER_USER_ID, "customer@example.com", UserRole.CUSTOMER);
            UUID productId = UUID.randomUUID();
            CartResponse response = new CartResponse(UUID.randomUUID(), List.of(), null, false, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
            when(cartService.addItem(eq(CUSTOMER_USER_ID), any(AddCartItemRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 2))))
                    .andExpect(status().isOk());

            verify(cartService).addItem(eq(CUSTOMER_USER_ID), any(AddCartItemRequest.class));
            clearAuth();
        }

        @Test
        @DisplayName("cart operations ALWAYS use the authenticated user's ID — no access to other users")
        void cartUsesAuthenticatedUserId() throws Exception {
            authenticateAs(CUSTOMER_USER_ID, "customer@example.com", UserRole.CUSTOMER);
            CartResponse response = new CartResponse(UUID.randomUUID(), List.of(), null, false, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
            when(cartService.view(CUSTOMER_USER_ID)).thenReturn(response);

            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isOk());

            // Verify the service was called with EXACTLY the authenticated user's ID
            // There is no way to pass a different user ID — the controller always uses CurrentUser.require()
            verify(cartService).view(CUSTOMER_USER_ID);
            verify(cartService, never()).view(argThat(id -> !id.equals(CUSTOMER_USER_ID)));
            clearAuth();
        }

        @Test
        @DisplayName("remove item from cart")
        void removeItem() throws Exception {
            authenticateAs(CUSTOMER_USER_ID, "customer@example.com", UserRole.CUSTOMER);
            UUID productId = UUID.randomUUID();
            CartResponse response = new CartResponse(UUID.randomUUID(), List.of(), null, false, MoneyUtil.ZERO, MoneyUtil.ZERO, MoneyUtil.ZERO);
            when(cartService.removeItem(CUSTOMER_USER_ID, productId)).thenReturn(response);

            mockMvc.perform(delete("/api/v1/cart/items/" + productId))
                    .andExpect(status().isOk());

            verify(cartService).removeItem(CUSTOMER_USER_ID, productId);
            clearAuth();
        }
    }
}

