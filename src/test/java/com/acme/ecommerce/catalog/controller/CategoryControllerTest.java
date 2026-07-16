package com.acme.ecommerce.catalog.controller;

import com.acme.ecommerce.catalog.dto.AttributeDefinitionRequest;
import com.acme.ecommerce.catalog.dto.AttributeDefinitionResponse;
import com.acme.ecommerce.catalog.dto.CategoryResponse;
import com.acme.ecommerce.catalog.dto.CreateCategoryRequest;
import com.acme.ecommerce.catalog.dto.UpdateCategoryRequest;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.catalog.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for the category controller.
 *
 * <p>The controller is intentionally thin and delegates hierarchy and attribute
 * behavior to CategoryService. Servlet validation/security filters are not part
 * of this unit spec.</p>
 */
@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController categoryController;

    @Test
    void create_shouldDelegateRequestToCategoryService() {
        // Given
        CreateCategoryRequest request = new CreateCategoryRequest(null, "Electronics", List.of());
        CategoryResponse expected = categoryResponse(UUID.randomUUID(), "Electronics");
        when(categoryService.create(request)).thenReturn(expected);

        // When
        CategoryResponse response = categoryController.create(request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(categoryService).create(request);
    }

    @Test
    void update_shouldDelegateCategoryIdAndRequestToCategoryService() {
        // Given
        UUID categoryId = UUID.randomUUID();
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, "Phones", true, List.of());
        CategoryResponse expected = categoryResponse(categoryId, "Phones");
        when(categoryService.update(categoryId, request)).thenReturn(expected);

        // When
        CategoryResponse response = categoryController.update(categoryId, request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(categoryService).update(categoryId, request);
    }

    @Test
    void addAttribute_shouldDelegateCategoryIdAndRequestToCategoryService() {
        // Given
        UUID categoryId = UUID.randomUUID();
        AttributeDefinitionRequest request = new AttributeDefinitionRequest("RAM", "ram", "RAM", AttributeType.SELECT, true, true, true, null, null, null, null, "8GB,16GB");
        AttributeDefinitionResponse expected = new AttributeDefinitionResponse(UUID.randomUUID(), "RAM", "ram", "RAM", AttributeType.SELECT, true, true, true, null, null, null, null, "8GB,16GB");
        when(categoryService.addAttribute(categoryId, request)).thenReturn(expected);

        // When
        AttributeDefinitionResponse response = categoryController.addAttribute(categoryId, request);

        // Then
        assertThat(response).isSameAs(expected);
        verify(categoryService).addAttribute(categoryId, request);
    }

    @Test
    void list_shouldDelegatePaginationToCategoryService() {
        // Given
        Page<CategoryResponse> expected = new PageImpl<>(List.of(categoryResponse(UUID.randomUUID(), "Electronics")));
        when(categoryService.list(1, 50)).thenReturn(expected);

        // When
        Page<CategoryResponse> response = categoryController.list(1, 50);

        // Then
        assertThat(response).isSameAs(expected);
        verify(categoryService).list(1, 50);
    }

    @Test
    void tree_shouldDelegateToCategoryService() {
        // Given
        List<CategoryResponse> expected = List.of(categoryResponse(UUID.randomUUID(), "Electronics"));
        when(categoryService.tree()).thenReturn(expected);

        // When
        List<CategoryResponse> response = categoryController.tree();

        // Then
        assertThat(response).isSameAs(expected);
        verify(categoryService).tree();
    }

    @Test
    void get_shouldDelegateCategoryIdToCategoryService() {
        // Given
        UUID categoryId = UUID.randomUUID();
        CategoryResponse expected = categoryResponse(categoryId, "Electronics");
        when(categoryService.get(categoryId)).thenReturn(expected);

        // When
        CategoryResponse response = categoryController.get(categoryId);

        // Then
        assertThat(response).isSameAs(expected);
        verify(categoryService).get(categoryId);
    }

    private CategoryResponse categoryResponse(UUID id, String name) {
        return new CategoryResponse(id, null, name, name.toLowerCase(), true, List.of());
    }
}
