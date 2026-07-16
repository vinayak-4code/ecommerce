package com.acme.ecommerce.cart;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.cart.dto.AddCartItemRequest;
import com.acme.ecommerce.cart.dto.ApplyCouponRequest;
import com.acme.ecommerce.cart.dto.UpdateCartItemQuantityRequest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.entity.CouponProductEnrollment;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.repository.CouponProductEnrollmentRepository;
import com.acme.ecommerce.coupon.repository.CouponRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("CartController – Functional Tests")
@Transactional
class CartControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/cart";

    @Autowired private CouponRepository couponRepository;
    @Autowired private CouponProductEnrollmentRepository enrollmentRepository;

    private Product publishedProduct;

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        seedCustomerWithProfile();
        SellerProfile seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        Category cat = seedCategory("Cart-Cat");
        publishedProduct = seedProduct(seller, cat, "Cart Prod", "SKU-CART", new BigDecimal("50"), ProductStatus.PUBLISHED);
        Warehouse wh = seedWarehouse(seller, "Cart WH", "WH-CART");
        seedInventory(publishedProduct.getId(), wh, 200);
        loginAsCustomer();
    }

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    private void addToCart(UUID productId, int qty) throws Exception {
        mockMvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON)
                .content(json(new AddCartItemRequest(productId, qty))));
    }

    // ── view ────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /cart")
    class View {
        @Test @DisplayName("success – empty cart view")
        void emptyCart() throws Exception {
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray());
        }

        @Test @DisplayName("success – cart with items")
        void withItems() throws Exception {
            addToCart(publishedProduct.getId(), 2);
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isNotEmpty());
        }
    }

    // ── add item ────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /cart/items")
    class AddItem {
        @Test @DisplayName("success – adds product to cart")
        void success() throws Exception {
            mockMvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddCartItemRequest(publishedProduct.getId(), 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isNotEmpty());
        }

        @Test @DisplayName("success – same product increments quantity")
        void increment() throws Exception {
            addToCart(publishedProduct.getId(), 1);
            mockMvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddCartItemRequest(publishedProduct.getId(), 2))))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("validation – null productId → 400")
        void nullProduct() throws Exception {
            mockMvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":null,\"quantity\":1}"))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – quantity zero → 400")
        void zeroQty() throws Exception {
            mockMvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddCartItemRequest(publishedProduct.getId(), 0))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("failure – non-existent product → 404")
        void productNotFound() throws Exception {
            mockMvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddCartItemRequest(UUID.randomUUID(), 1))))
                    .andExpect(status().isNotFound());
        }

        @Test @DisplayName("failure – exceeds available inventory → 409")
        void exceedsInventory() throws Exception {
            mockMvc.perform(post(BASE + "/items").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AddCartItemRequest(publishedProduct.getId(), 999))))
                    .andExpect(status().isConflict());
        }
    }

    // ── update quantity ─────────────────────────────────────────────────

    @Nested @DisplayName("PUT /cart/items/{productId}")
    class UpdateQuantity {
        @Test @DisplayName("success – updates quantity")
        void success() throws Exception {
            addToCart(publishedProduct.getId(), 1);
            mockMvc.perform(put(BASE + "/items/" + publishedProduct.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UpdateCartItemQuantityRequest(5))))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("edge – zero quantity removes item")
        void zeroRemoves() throws Exception {
            addToCart(publishedProduct.getId(), 1);
            mockMvc.perform(put(BASE + "/items/" + publishedProduct.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UpdateCartItemQuantityRequest(0))))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("validation – negative quantity → 400")
        void negativeQty() throws Exception {
            mockMvc.perform(put(BASE + "/items/" + publishedProduct.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UpdateCartItemQuantityRequest(-1))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── remove item ─────────────────────────────────────────────────────

    @Nested @DisplayName("DELETE /cart/items/{productId}")
    class RemoveItem {
        @Test @DisplayName("success – removes item from cart")
        void success() throws Exception {
            addToCart(publishedProduct.getId(), 1);
            mockMvc.perform(delete(BASE + "/items/" + publishedProduct.getId()))
                    .andExpect(status().isOk());
        }
    }

    // ── eligible coupons ────────────────────────────────────────────────

    @Nested @DisplayName("GET /cart/eligible-coupons")
    class EligibleCoupons {
        @Test @DisplayName("success – returns coupon list")
        void eligibleCoupons() throws Exception {
            mockMvc.perform(get(BASE + "/eligible-coupons"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ── apply coupon ────────────────────────────────────────────────────

    @Nested @DisplayName("POST /cart/coupon")
    class ApplyCoupon {
        @Test @DisplayName("failure – non-existent coupon → 404")
        void unknown() throws Exception {
            mockMvc.perform(post(BASE + "/coupon").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ApplyCouponRequest("NOSUCH"))))
                    .andExpect(status().isNotFound());
        }

        @Test @DisplayName("validation – blank code → 400")
        void blankCode() throws Exception {
            mockMvc.perform(post(BASE + "/coupon").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new ApplyCouponRequest(""))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── remove coupon ───────────────────────────────────────────────────

    @Nested @DisplayName("DELETE /cart/coupon")
    class RemoveCoupon {
        @Test @DisplayName("success – removes coupon (even if none applied)")
        void success() throws Exception {
            mockMvc.perform(delete(BASE + "/coupon"))
                    .andExpect(status().isOk());
        }
    }
}

