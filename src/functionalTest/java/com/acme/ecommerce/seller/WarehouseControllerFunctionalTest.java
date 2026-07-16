package com.acme.ecommerce.seller;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.seller.dto.CreateWarehouseRequest;
import com.acme.ecommerce.seller.dto.UpdateWarehouseRequest;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
import com.acme.ecommerce.seller.enums.WarehouseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("WarehouseController – Functional Tests")
@Transactional
class WarehouseControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/sellers/warehouses";
    private SellerProfile seller;

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        loginAsSeller();
    }

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    private CreateWarehouseRequest validCreate() {
        return new CreateWarehouseRequest("Main WH", "WH-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(),
                "123 St", "NYC", "NY", "US", "10001");
    }

    // ── create ──────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /sellers/warehouses")
    class Create {
        @Test @DisplayName("success – warehouse created")
        void success() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json(validCreate())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Main WH"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test @DisplayName("validation – blank name → 400")
        void blankName() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateWarehouseRequest("", "WH-X", "St", "C", "S", "US", "12345"))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – blank code → 400")
        void blankCode() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateWarehouseRequest("WH", "", "St", "C", "S", "US", "12345"))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("edge – name at 160 chars succeeds")
        void maxName() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateWarehouseRequest("A".repeat(160), "WH-MAX", "St", "C", "S", "US", "12345"))))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("validation – name exceeds 160 → 400")
        void nameTooLong() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateWarehouseRequest("A".repeat(161), "WH-OVR", "St", "C", "S", "US", "12345"))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /sellers/warehouses")
    class ListWarehouses {
        @Test @DisplayName("success – returns paginated list")
        void list() throws Exception {
            seedWarehouse(seller, "WH-List", "WH-L");
            mockMvc.perform(get(BASE).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }

        @Test @DisplayName("edge – large page → empty")
        void emptyPage() throws Exception {
            mockMvc.perform(get(BASE).param("page", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    // ── update ──────────────────────────────────────────────────────────

    @Nested @DisplayName("PUT /sellers/warehouses/{id}")
    class Update {
        @Test @DisplayName("success – warehouse updated")
        void success() throws Exception {
            Warehouse w = seedWarehouse(seller, "Old WH", "WH-OLD");
            var req = new UpdateWarehouseRequest("New WH", "456 New St", "Boston", "MA", "US", "02101", WarehouseStatus.ACTIVE);
            mockMvc.perform(put(BASE + "/" + w.getId()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New WH"));
        }

        @Test @DisplayName("success – set to INACTIVE")
        void setInactive() throws Exception {
            Warehouse w = seedWarehouse(seller, "Deact WH", "WH-DEA");
            var req = new UpdateWarehouseRequest("Deact WH", "St", "C", "S", "US", "12345", WarehouseStatus.INACTIVE);
            mockMvc.perform(put(BASE + "/" + w.getId()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INACTIVE"));
        }

        @Test @DisplayName("failure – not found → 404")
        void notFound() throws Exception {
            var req = new UpdateWarehouseRequest("X", "St", "C", "S", "US", "12345", WarehouseStatus.ACTIVE);
            mockMvc.perform(put(BASE + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isNotFound());
        }

        @Test @DisplayName("validation – blank name → 400")
        void blankName() throws Exception {
            Warehouse w = seedWarehouse(seller, "Val WH", "WH-VAL");
            var req = new UpdateWarehouseRequest("", "St", "C", "S", "US", "12345", WarehouseStatus.ACTIVE);
            mockMvc.perform(put(BASE + "/" + w.getId()).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Nested @DisplayName("DELETE /sellers/warehouses/{id}")
    class Delete {
        @Test @DisplayName("success – empty warehouse deleted")
        void success() throws Exception {
            Warehouse w = seedWarehouse(seller, "Del WH", "WH-DEL");
            mockMvc.perform(delete(BASE + "/" + w.getId()))
                    .andExpect(status().isNoContent());
        }

        @Test @DisplayName("failure – warehouse with inventory → 400")
        void hasInventory() throws Exception {
            Warehouse w = seedWarehouse(seller, "Inv WH", "WH-INV");
            var cat = seedCategory("Del-Cat");
            var prod = seedProduct(seller, cat, "Del Prod", "SKU-DEL", new java.math.BigDecimal("10"), com.acme.ecommerce.catalog.enums.ProductStatus.DRAFT);
            seedInventory(prod.getId(), w, 50);
            mockMvc.perform(delete(BASE + "/" + w.getId()))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("failure – not found → 404")
        void notFound() throws Exception {
            mockMvc.perform(delete(BASE + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }
}

