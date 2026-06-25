package com.acme.ecommerce.catalog;

import com.acme.ecommerce.catalog.controller.CategoryController;
import com.acme.ecommerce.catalog.dto.CategoryResponse;
import com.acme.ecommerce.catalog.dto.CreateCategoryRequest;
import com.acme.ecommerce.catalog.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @Test
    void productAdminCanCreateCategoryWithPredefinedAttributes() throws Exception {
        UUID categoryId = UUID.randomUUID();
        when(categoryService.create(any(CreateCategoryRequest.class))).thenReturn(
                new CategoryResponse(categoryId, null, "Electronics", "electronics", true, List.of())
        );

        String payload = """
                {
                  "name": "Electronics",
                  "attributes": [
                    {"name": "RAM", "code": "ram", "attributeType": "STRING", "required": true, "searchable": true}
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.name").value("Electronics"));
    }
}
