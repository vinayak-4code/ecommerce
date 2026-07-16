package com.acme.ecommerce.catalog;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.catalog.dto.*;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.seller.entity.SellerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ProductController – Functional Tests")
@Transactional
class ProductControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/products";
    private Category category;
    private SellerProfile seller;

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        seedAdmin();
        category = seedCategory("Test-Product-Cat");
        seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        loginAsSeller();
    }

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    private CreateProductRequest validCreate() {
        return new CreateProductRequest(category.getId(), "Widget", "Desc",
                "SKU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                new BigDecimal("99.99"), CurrencyCode.USD, List.of());
    }

    private Product seedDraftProduct() {
        return seedProduct(seller, category, "Draft Prod", "SKU-DRAFT", new BigDecimal("50.00"), ProductStatus.DRAFT);
    }

    private Product seedPublishedProduct() {
        return seedProduct(seller, category, "Pub Prod", "SKU-PUB", new BigDecimal("75.00"), ProductStatus.PUBLISHED);
    }

    // ── create ──────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /products")
    class Create {
        @Test @DisplayName("success – draft product created")
        void success() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(validCreate())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Widget"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test @DisplayName("validation – blank name → 400")
        void blankName() throws Exception {
            var req = new CreateProductRequest(category.getId(), "", "d", "SKU-X", new BigDecimal("10"), CurrencyCode.USD, List.of());
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – null categoryId → 400")
        void nullCategory() throws Exception {
            var req = new CreateProductRequest(null, "X", "d", "SKU-X", new BigDecimal("10"), CurrencyCode.USD, List.of());
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – price zero → 400")
        void zeroPrice() throws Exception {
            var req = new CreateProductRequest(category.getId(), "X", "d", "SKU-X", BigDecimal.ZERO, CurrencyCode.USD, List.of());
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("edge – name 220 chars succeeds")
        void maxName() throws Exception {
            var req = new CreateProductRequest(category.getId(), "A".repeat(220), "d", "SKU-MAX", new BigDecimal("10"), CurrencyCode.USD, List.of());
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("validation – name 221 chars → 400")
        void nameTooLong() throws Exception {
            var req = new CreateProductRequest(category.getId(), "A".repeat(221), "d", "SKU-OVER", new BigDecimal("10"), CurrencyCode.USD, List.of());
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /products")
    class ListProducts {
        @Test @DisplayName("success – returns seller products")
        void list() throws Exception {
            seedDraftProduct();
            mockMvc.perform(get(BASE).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }
    }

    // ── get ──────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /products/{id}")
    class GetById {
        @Test @DisplayName("success – published product returned")
        void found() throws Exception {
            Product p = seedPublishedProduct();
            mockMvc.perform(get(BASE + "/" + p.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Pub Prod"));
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }

        @Test @DisplayName("failure – draft product → 400 business rule")
        void draftNotAvailable() throws Exception {
            Product p = seedDraftProduct();
            mockMvc.perform(get(BASE + "/" + p.getId()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── update ──────────────────────────────────────────────────────────

    @Nested @DisplayName("PUT /products/{id}")
    class Update {
        @Test @DisplayName("success – product updated")
        void success() throws Exception {
            Product p = seedDraftProduct();
            var req = new UpdateProductRequest(category.getId(), "Updated", "Updated desc", "SKU-UPD", new BigDecimal("199.99"), CurrencyCode.USD, List.of());
            mockMvc.perform(put(BASE + "/" + p.getId()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated"));
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            var req = new UpdateProductRequest(category.getId(), "X", "d", "SKU-X", new BigDecimal("10"), CurrencyCode.USD, List.of());
            mockMvc.perform(put(BASE + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── publish / unpublish ─────────────────────────────────────────────

    @Nested @DisplayName("PATCH /products/{id}/publish")
    class Publish {
        @Test @DisplayName("success – draft → published")
        void success() throws Exception {
            Product p = seedDraftProduct();
            mockMvc.perform(patch(BASE + "/" + p.getId() + "/publish"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"));
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            mockMvc.perform(patch(BASE + "/" + UUID.randomUUID() + "/publish"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested @DisplayName("PATCH /products/{id}/unpublish")
    class Unpublish {
        @Test @DisplayName("success – published → unpublished")
        void success() throws Exception {
            Product p = seedPublishedProduct();
            mockMvc.perform(patch(BASE + "/" + p.getId() + "/unpublish"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UNPUBLISHED"));
        }
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Nested @DisplayName("DELETE /products/{id}")
    class Delete {
        @Test @DisplayName("success – soft-deletes product")
        void success() throws Exception {
            Product p = seedDraftProduct();
            mockMvc.perform(delete(BASE + "/" + p.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    // ── versions ────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /products/{id}/versions")
    class Versions {
        @Test @DisplayName("success – returns version list")
        void success() throws Exception {
            Product p = seedDraftProduct();
            // create a version via the API
            mockMvc.perform(patch(BASE + "/" + p.getId() + "/publish"));
            mockMvc.perform(get(BASE + "/" + p.getId() + "/versions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID() + "/versions"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── rollback ────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /products/{id}/rollback/{version}")
    class Rollback {
        @Test @DisplayName("failure – non-existent product → 404")
        void notFoundProduct() throws Exception {
            mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/rollback/1"))
                    .andExpect(status().isNotFound());
        }

        @Test @DisplayName("failure – non-existent version → 4xx")
        void badVersion() throws Exception {
            Product p = seedDraftProduct();
            mockMvc.perform(post(BASE + "/" + p.getId() + "/rollback/999"))
                    .andExpect(status().is4xxClientError());
        }
    }
}

