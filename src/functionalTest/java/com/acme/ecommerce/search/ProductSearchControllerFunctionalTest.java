package com.acme.ecommerce.search;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.search.entity.ProductSearchDocument;
import com.acme.ecommerce.search.repository.ProductSearchDocumentRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("ProductSearchController – Functional Tests")
@Transactional
class ProductSearchControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/search/products";

    @Autowired private ProductSearchDocumentRepository searchDocRepo;

    private Category category;

    @BeforeEach
    void seed() {
        seedSellerWithProfile();
        SellerProfile seller = sellerProfileRepository.findByUserAccountId(sellerUserId).orElseThrow();
        category = seedCategory("Search-Cat");
        // Seed search documents directly
        seedSearchDocument(seller.getId(), category, "Wireless Mouse", "SKU-WM", new BigDecimal("29.99"), 50);
        seedSearchDocument(seller.getId(), category, "Mechanical Keyboard", "SKU-MK", new BigDecimal("89.99"), 100);
    }

    private void seedSearchDocument(UUID sellerId, Category cat, String name, String sku, BigDecimal price, int qty) {
        ProductSearchDocument doc = new ProductSearchDocument();
        doc.setProductId(UUID.randomUUID());
        doc.setSellerId(sellerId);
        doc.setCategoryId(cat.getId());
        doc.setCategoryName(cat.getName());
        doc.setName(name);
        doc.setDescription(name + " description");
        doc.setSku(sku);
        doc.setPrice(price);
        doc.setCurrency(CurrencyCode.USD);
        doc.setStatus(ProductStatus.PUBLISHED);
        doc.setAttributesJson("{}");
        doc.setTotalAvailableQuantity(qty);
        searchDocRepo.save(doc);
    }

    // ── search ──────────────────────────────────────────────────────────

    @Nested @DisplayName("GET /search/products")
    class Search {
        @Test @DisplayName("success – no filters returns all published")
        void noFilters() throws Exception {
            mockMvc.perform(get(BASE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }

        @Test @DisplayName("success – text query filters results")
        void textQuery() throws Exception {
            mockMvc.perform(get(BASE).param("q", "Mouse"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].name").value("Wireless Mouse"));
        }

        @Test @DisplayName("success – category filter")
        void categoryFilter() throws Exception {
            mockMvc.perform(get(BASE).param("categoryId", category.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }

        @Test @DisplayName("success – sorting by price DESC")
        void sortByPrice() throws Exception {
            mockMvc.perform(get(BASE).param("sortBy", "price").param("direction", "DESC"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Mechanical Keyboard"));
        }

        @Test @DisplayName("success – pagination")
        void pagination() throws Exception {
            mockMvc.perform(get(BASE).param("page", "0").param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test @DisplayName("edge – no matching query → empty")
        void noMatch() throws Exception {
            mockMvc.perform(get(BASE).param("q", "ZZZZZ"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test @DisplayName("edge – non-existent category → empty results")
        void badCategory() throws Exception {
            mockMvc.perform(get(BASE).param("categoryId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }
}

