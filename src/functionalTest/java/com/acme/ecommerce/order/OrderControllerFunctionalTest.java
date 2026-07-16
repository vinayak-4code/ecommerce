package com.acme.ecommerce.order;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.order.dto.CreateOrderRequest;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("OrderController – Functional Tests")
@Transactional
class OrderControllerFunctionalTest extends BaseFunctionalTest {

    private static final String ORDERS = "/api/v1/orders";
    private static final String CART   = "/api/v1/cart";

    private Product product;

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        seedCustomerWithProfile();
        SellerProfile seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        Category cat = seedCategory("Order-Cat");
        product = seedProduct(seller, cat, "Order Prod", "SKU-ORD", new BigDecimal("25"), ProductStatus.PUBLISHED);
        Warehouse wh = seedWarehouse(seller, "Order WH", "WH-ORD");
        seedInventory(product.getId(), wh, 500);
        loginAsCustomer();
    }

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    private void addToCart(int qty) throws Exception {
        mockMvc.perform(post(CART + "/items").contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddCartItemRequest(product.getId(), qty))));
    }

    private String placeOrderAndReturnId() throws Exception {
        addToCart(2);
        var result = mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest("123 Ship St"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    // ── place order ─────────────────────────────────────────────────────

    @Nested @DisplayName("POST /orders")
    class PlaceOrder {
        @Test @DisplayName("success – order created with lines")
        void success() throws Exception {
            addToCart(1);
            mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateOrderRequest("456 Delivery Ave"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.orderNumber").isNotEmpty())
                    .andExpect(jsonPath("$.status").value("PLACED"))
                    .andExpect(jsonPath("$.lines").isNotEmpty());
        }

        @Test @DisplayName("failure – empty cart → 400")
        void emptyCart() throws Exception {
            mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateOrderRequest("Address"))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – shipping address over 1000 chars → 400")
        void addressTooLong() throws Exception {
            mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateOrderRequest("A".repeat(1001)))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("edge – shipping address exactly 1000 chars succeeds")
        void maxAddress() throws Exception {
            addToCart(1);
            mockMvc.perform(post(ORDERS).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateOrderRequest("A".repeat(1000)))))
                    .andExpect(status().isCreated());
        }
    }

    // ── get order ───────────────────────────────────────────────────────

    @Nested @DisplayName("GET /orders/{id}")
    class GetOrder {
        @Test @DisplayName("success – returns order")
        void found() throws Exception {
            String orderId = placeOrderAndReturnId();
            mockMvc.perform(get(ORDERS + "/" + orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId));
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            mockMvc.perform(get(ORDERS + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    // ── list orders ─────────────────────────────────────────────────────

    @Nested @DisplayName("GET /orders")
    class ListOrders {
        @Test @DisplayName("success – paginated list")
        void list() throws Exception {
            placeOrderAndReturnId();
            mockMvc.perform(get(ORDERS).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }

        @Test @DisplayName("edge – large page → empty")
        void emptyPage() throws Exception {
            mockMvc.perform(get(ORDERS).param("page", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }
}

