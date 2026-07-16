package com.acme.ecommerce.catalog;

import com.acme.ecommerce.BaseFunctionalTest;
import com.acme.ecommerce.catalog.dto.*;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.enums.AttributeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("CategoryController – Functional Tests")
@Transactional
class CategoryControllerFunctionalTest extends BaseFunctionalTest {

    private static final String BASE = "/api/v1/categories";

    @BeforeEach
    void seed() { seedAdmin(); loginAsAdmin(); }

    private String json(Object o) throws Exception { return objectMapper.writeValueAsString(o); }

    private AttributeDefinitionRequest attr(String name, String code) {
        return new AttributeDefinitionRequest(name, code, name, AttributeType.STRING, false, true, true, null, null, null, null, null);
    }

    // ── create ──────────────────────────────────────────────────────────

    @Nested @DisplayName("POST /categories")
    class Create {
        @Test @DisplayName("success – root category created")
        void rootCategory() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateCategoryRequest(null, "Electronics", List.of()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("Electronics"));
        }

        @Test @DisplayName("success – child category with attributes")
        void childWithAttributes() throws Exception {
            Category parent = seedCategory("Parent-Cat");
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateCategoryRequest(parent.getId(), "Phones", List.of(attr("RAM", "ram"))))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Phones"));
        }

        @Test @DisplayName("failure – duplicate slug → 409")
        void duplicateSlug() throws Exception {
            seedCategory("Duplicate");
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateCategoryRequest(null, "Duplicate", List.of()))))
                    .andExpect(status().isConflict());
        }

        @Test @DisplayName("validation – blank name → 400")
        void blankName() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateCategoryRequest(null, "", List.of()))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("edge – name at max 140 chars succeeds")
        void maxLengthName() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateCategoryRequest(null, "A".repeat(140), List.of()))))
                    .andExpect(status().isCreated());
        }

        @Test @DisplayName("validation – name exceeds 140 chars → 400")
        void nameTooLong() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateCategoryRequest(null, "A".repeat(141), List.of()))))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("failure – non-existent parentId → 404")
        void badParent() throws Exception {
            mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new CreateCategoryRequest(UUID.randomUUID(), "Orphan", List.of()))))
                    .andExpect(status().isNotFound());
        }
    }

    // ── update ──────────────────────────────────────────────────────────

    @Nested @DisplayName("PUT /categories/{id}")
    class Update {
        @Test @DisplayName("success – rename category")
        void rename() throws Exception {
            Category c = seedCategory("Old-Name");
            mockMvc.perform(put(BASE + "/" + c.getId()).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UpdateCategoryRequest(null, "New-Name", true, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New-Name"));
        }

        @Test @DisplayName("failure – non-existent id → 404")
        void notFound() throws Exception {
            mockMvc.perform(put(BASE + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UpdateCategoryRequest(null, "X", true, null))))
                    .andExpect(status().isNotFound());
        }
    }

    // ── add attribute ───────────────────────────────────────────────────

    @Nested @DisplayName("POST /categories/{id}/attributes")
    class AddAttribute {
        @Test @DisplayName("success – add attribute to leaf category")
        void addAttribute() throws Exception {
            Category leaf = seedCategory("Leaf-Cat");
            mockMvc.perform(post(BASE + "/" + leaf.getId() + "/attributes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(attr("Storage", "storage"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Storage"));
        }

        @Test @DisplayName("validation – blank attribute name → 400")
        void blankAttrName() throws Exception {
            Category leaf = seedCategory("Leaf-Cat2");
            mockMvc.perform(post(BASE + "/" + leaf.getId() + "/attributes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AttributeDefinitionRequest("", "", null, AttributeType.STRING, false, false, false, null, null, null, null, null))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── list / tree / get ───────────────────────────────────────────────

    @Nested @DisplayName("GET /categories")
    class ListCategories {
        @Test @DisplayName("success – paginated list")
        void list() throws Exception {
            seedCategory("Cat-A");
            mockMvc.perform(get(BASE).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isNotEmpty());
        }

        @Test @DisplayName("edge – large page → empty")
        void emptyPage() throws Exception {
            mockMvc.perform(get(BASE).param("page", "999"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty());
        }
    }

    @Nested @DisplayName("GET /categories/tree")
    class Tree {
        @Test @DisplayName("success – returns flat array")
        void tree() throws Exception {
            seedCategory("Tree-Cat");
            mockMvc.perform(get(BASE + "/tree"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Nested @DisplayName("GET /categories/{id}")
    class GetById {
        @Test @DisplayName("success – returns category")
        void found() throws Exception {
            Category c = seedCategory("Get-Cat");
            mockMvc.perform(get(BASE + "/" + c.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Get-Cat"));
        }

        @Test @DisplayName("failure – non-existent → 404")
        void notFound() throws Exception {
            mockMvc.perform(get(BASE + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }
}

