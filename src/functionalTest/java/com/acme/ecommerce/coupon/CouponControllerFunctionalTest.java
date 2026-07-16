package com.acme.ecommerce.coupon;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.coupon.dto.CreateCouponRequest;
import com.acme.ecommerce.coupon.dto.UpdateCouponRequest;
import com.acme.ecommerce.coupon.entity.Coupon;
import com.acme.ecommerce.coupon.enums.CouponStatus;
import com.acme.ecommerce.coupon.enums.DiscountScope;
import com.acme.ecommerce.coupon.enums.DiscountType;
import com.acme.ecommerce.coupon.repository.CouponRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
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
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("CouponController – Functional Tests")
@Transactional
class CouponControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/coupons";

    @Autowired private CouponRepository couponRepository;

    private Category category;
    private SellerProfile seller;
    private Product product;

    @BeforeEach
    void seed() {
        seedAdmin();
        seedSellerWithProfile();
        seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        category = seedCategory("Coupon-Cat");
        product = seedProduct(seller, category, "Coupon Prod", "SKU-CPN", new BigDecimal("100"), ProductStatus.PUBLISHED);
        loginAsAdmin();
    }

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    private Coupon seedCoupon(String code) {
        Coupon c = new Coupon();
        c.setCode(code.toUpperCase());
        c.setDescription("Test coupon");
        c.setDiscountType(DiscountType.FLAT);
        c.setDiscountScope(DiscountScope.CART);
        c.setValue(new BigDecimal("10.00"));
        c.setMinCartAmount(BigDecimal.ZERO);
        c.setStartsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        c.setEndsAt(Instant.now().plus(30, ChronoUnit.DAYS));
        c.setStatus(CouponStatus.ACTIVE);
        return couponRepository.save(c);
    }

    private CreateCouponRequest validCreate(String code) {
        return new CreateCouponRequest(code, "Test", DiscountType.FLAT, DiscountScope.CART,
                new BigDecimal("10"), null, BigDecimal.ZERO,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), Set.of());
    }

    // ── create ──────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /coupons")
    class Create {
        @Test @DisplayName("success – FLAT coupon created")
        void success() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(validCreate("FLAT10"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("FLAT10"))
                    .andExpect(jsonPath("$.discountType").value("FLAT"));
        }

        @Test @DisplayName("success – UPTO_PERCENT_OFF coupon")
        void percentOff() throws Exception {
            var req = new CreateCouponRequest("PCT20", "20%", DiscountType.UPTO_PERCENT_OFF, DiscountScope.CART,
                    new BigDecimal("20"), new BigDecimal("50"), BigDecimal.ZERO,
                    Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), Set.of());
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.discountType").value("UPTO_PERCENT_OFF"));
        }

        @Test @DisplayName("failure – duplicate code → 409")
        void duplicateCode() throws Exception {
            seedCoupon("DUP-CODE");
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(validCreate("DUP-CODE"))))
                    .andExpect(status().isConflict());
        }

        @Test @DisplayName("validation – blank code → 400")
        void blankCode() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(validCreate(""))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – value below min → 400")
        void valueTooLow() throws Exception {
            var req = new CreateCouponRequest("LOW", "d", DiscountType.FLAT, DiscountScope.CART,
                    BigDecimal.ZERO, null, null, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), Set.of());
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── update ──────────────────────────────────────────────────────────

    @Nested @DisplayName("PUT /coupons/{id}")
    class Update {
        @Test @DisplayName("success – coupon updated")
        void success() throws Exception {
            Coupon c = seedCoupon("UPD-COUPON");
            var req = new UpdateCouponRequest("UPD-COUPON", "Updated desc", DiscountType.FLAT, DiscountScope.CART,
                    new BigDecimal("15"), null, BigDecimal.ZERO, CouponStatus.ACTIVE,
                    Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(60, ChronoUnit.DAYS), Set.of());
            mockMvc.perform(put(BASE + "/" + c.getId()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk());
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            var req = new UpdateCouponRequest("X", "d", DiscountType.FLAT, DiscountScope.CART,
                    new BigDecimal("10"), null, null, CouponStatus.ACTIVE,
                    Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS), Set.of());
            mockMvc.perform(put(BASE + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /coupons")
    class ListCoupons {
        @Test @DisplayName("success – paginated list")
        void list() throws Exception {
            seedCoupon("LIST-1");
            mockMvc.perform(get(BASE).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }
    }

    // ── get by code ─────────────────────────────────────────────────────

    @Nested @DisplayName("GET /coupons/{code}")
    class GetByCode {
        @Test @DisplayName("success – coupon returned")
        void found() throws Exception {
            seedCoupon("GET-CODE");
            mockMvc.perform(get(BASE + "/GET-CODE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("GET-CODE"));
        }

        @Test @DisplayName("failure – unknown code → 404")
        void notFound() throws Exception {
            mockMvc.perform(get(BASE + "/UNKNOWN"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── eligible for product ────────────────────────────────────────────

    @Nested @DisplayName("GET /coupons/products/{id}/eligible")
    class EligibleForProduct {
        @Test @DisplayName("success – returns list (possibly empty)")
        void eligible() throws Exception {
            mockMvc.perform(get(BASE + "/products/" + product.getId() + "/eligible"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    // ── enroll / unenroll ───────────────────────────────────────────────

    @Nested @DisplayName("POST /coupons/{code}/products/{id}/enroll")
    class Enroll {
        @Test @DisplayName("success – seller enrolls product")
        void success() throws Exception {
            Coupon c = seedCoupon("ENROLL-1");
            loginAsSeller();
            mockMvc.perform(post(BASE + "/" + c.getCode() + "/products/" + product.getId() + "/enroll"))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("failure – unknown coupon → 404")
        void unknownCoupon() throws Exception {
            loginAsSeller();
            mockMvc.perform(post(BASE + "/NOSUCH/products/" + product.getId() + "/enroll"))
                    .andExpect(status().isNotFound());
        }

        @Test @DisplayName("failure – non-existent product → 404")
        void unknownProduct() throws Exception {
            Coupon c = seedCoupon("ENROLL-2");
            loginAsSeller();
            mockMvc.perform(post(BASE + "/" + c.getCode() + "/products/" + UUID.randomUUID() + "/enroll"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("DELETE /coupons/{code}/products/{id}/enroll")
    class Unenroll {
        @Test @DisplayName("success – seller unenrolls product")
        void success() throws Exception {
            Coupon c = seedCoupon("UNENRL");
            loginAsSeller();
            // enroll first
            mockMvc.perform(post(BASE + "/" + c.getCode() + "/products/" + product.getId() + "/enroll"));
            // unenroll
            mockMvc.perform(delete(BASE + "/" + c.getCode() + "/products/" + product.getId() + "/enroll"))
                    .andExpect(status().isNoContent());
        }
    }
}

