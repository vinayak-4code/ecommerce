package com.acme.ecommerce.inventory;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.inventory.dto.BulkInventoryUpdateRequest;
import com.acme.ecommerce.inventory.dto.InventoryAdjustmentRequest;
import com.acme.ecommerce.inventory.dto.InventoryUpdateRequest;
import com.acme.ecommerce.inventory.enums.InventoryAdjustmentReason;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.entity.Warehouse;
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

@DisplayName("InventoryController – Functional Tests")
@Transactional
class InventoryControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/inventory";

    private SellerProfile seller;
    private Warehouse warehouse;
    private Product product;

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        Category cat = seedCategory("Inv-Cat");
        product = seedProduct(seller, cat, "Inv Prod", "SKU-INV", new BigDecimal("50"), ProductStatus.DRAFT);
        warehouse = seedWarehouse(seller, "Inv WH", "WH-INV");
        loginAsSeller();
    }

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    // ── PUT /inventory ──────────────────────────────────────────────────

    @Nested @DisplayName("PUT /inventory")
    class UpdateInventory {
        @Test @DisplayName("success – sets absolute quantity")
        void success() throws Exception {
            var req = new InventoryUpdateRequest(product.getId(), warehouse.getId(), 100, InventoryAdjustmentReason.INITIAL_STOCK);
            mockMvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(100));
        }

        @Test @DisplayName("success – zero quantity (out of stock)")
        void zeroQty() throws Exception {
            var req = new InventoryUpdateRequest(product.getId(), warehouse.getId(), 0, InventoryAdjustmentReason.STOCK_CORRECTION);
            mockMvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(0));
        }

        @Test @DisplayName("validation – negative quantity → 400")
        void negativeQty() throws Exception {
            var req = new InventoryUpdateRequest(product.getId(), warehouse.getId(), -1, InventoryAdjustmentReason.INITIAL_STOCK);
            mockMvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – null productId → 400")
        void nullProduct() throws Exception {
            mockMvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":null,\"warehouseId\":\"" + warehouse.getId() + "\",\"availableQuantity\":10,\"reason\":\"INITIAL_STOCK\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("validation – null reason → 400")
        void nullReason() throws Exception {
            mockMvc.perform(put(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + product.getId() + "\",\"warehouseId\":\"" + warehouse.getId() + "\",\"availableQuantity\":10,\"reason\":null}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /inventory/bulk ─────────────────────────────────────────────

    @Nested @DisplayName("PUT /inventory/bulk")
    class BulkUpdate {
        @Test @DisplayName("success – bulk update")
        void success() throws Exception {
            var req = new BulkInventoryUpdateRequest(List.of(
                    new InventoryUpdateRequest(product.getId(), warehouse.getId(), 50, InventoryAdjustmentReason.INITIAL_STOCK)));
            mockMvc.perform(put(BASE + "/bulk").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].availableQuantity").value(50));
        }

        @Test @DisplayName("validation – empty list → 400")
        void emptyList() throws Exception {
            mockMvc.perform(put(BASE + "/bulk").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new BulkInventoryUpdateRequest(List.of()))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── PUT /inventory/adjustments ──────────────────────────────────────

    @Nested @DisplayName("PUT /inventory/adjustments")
    class Adjust {
        @Test @DisplayName("success – positive delta")
        void positiveDelta() throws Exception {
            seedInventory(product.getId(), warehouse, 100);
            var req = new InventoryAdjustmentRequest(product.getId(), warehouse.getId(), 25, InventoryAdjustmentReason.RETURN_RECEIVED);
            mockMvc.perform(put(BASE + "/adjustments").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(125));
        }

        @Test @DisplayName("success – negative delta")
        void negativeDelta() throws Exception {
            seedInventory(product.getId(), warehouse, 100);
            var req = new InventoryAdjustmentRequest(product.getId(), warehouse.getId(), -10, InventoryAdjustmentReason.DAMAGED_STOCK);
            mockMvc.perform(put(BASE + "/adjustments").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(90));
        }

        @Test @DisplayName("failure – adjustment would go negative → 409")
        void wouldGoNegative() throws Exception {
            seedInventory(product.getId(), warehouse, 5);
            var req = new InventoryAdjustmentRequest(product.getId(), warehouse.getId(), -10, InventoryAdjustmentReason.DAMAGED_STOCK);
            mockMvc.perform(put(BASE + "/adjustments").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isConflict());
        }

        @Test @DisplayName("failure – zero change → 400")
        void zeroDelta() throws Exception {
            seedInventory(product.getId(), warehouse, 100);
            var req = new InventoryAdjustmentRequest(product.getId(), warehouse.getId(), 0, InventoryAdjustmentReason.STOCK_CORRECTION);
            mockMvc.perform(put(BASE + "/adjustments").contentType(MediaType.APPLICATION_JSON).content(json(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /inventory ──────────────────────────────────────────────────

    @Nested @DisplayName("GET /inventory")
    class ListInventory {
        @Test @DisplayName("success – returns seller inventory")
        void list() throws Exception {
            seedInventory(product.getId(), warehouse, 42);
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].availableQuantity").value(42));
        }
    }

    // ── GET /inventory/products/{pid}/warehouses/{wid} ──────────────────

    @Nested @DisplayName("GET /inventory/products/{pid}/warehouses/{wid}")
    class GetByProductAndWarehouse {
        @Test @DisplayName("success – returns inventory")
        void found() throws Exception {
            seedInventory(product.getId(), warehouse, 77);
            mockMvc.perform(get(BASE + "/products/" + product.getId() + "/warehouses/" + warehouse.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(77));
        }

        @Test @DisplayName("failure – not found → 400 (business exception)")
        void notFound() throws Exception {
            mockMvc.perform(get(BASE + "/products/" + UUID.randomUUID() + "/warehouses/" + UUID.randomUUID()))
                    .andExpect(status().isBadRequest());
        }
    }
}

