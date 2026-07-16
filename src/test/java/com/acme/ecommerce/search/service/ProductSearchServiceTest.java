package com.acme.ecommerce.search.service;

import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.service.CategoryService;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.search.dto.ProductSearchResponse;
import com.acme.ecommerce.search.entity.ProductSearchDocument;
import com.acme.ecommerce.search.repository.ProductSearchDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit specification for public product listing/search.
 *
 * <p>The search service reads from a denormalized projection. These tests verify
 * query trimming, category tree delegation, safe sorting, bounded pagination,
 * attribute filtering, and projection-to-response mapping.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductSearchDocumentRepository repository;

    @Mock
    private CategoryService categoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductSearchService productSearchService;

    @Test
    void search_shouldSearchAllCategoriesWithNullQueryAndSafeDefaultSort_whenCategoryIsNotProvided() {
        // Given
        ProductSearchDocument document = document(UUID.randomUUID(), UUID.randomUUID(), "Keyboard", "{\"color\":\"Black\"}");
        when(repository.searchAllCategories(eq(null), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        // When
        Page<ProductSearchResponse> response = productSearchService.search(
                "   ",
                null,
                Map.of(),
                -5,
                500,
                "unsafeField",
                Sort.Direction.DESC
        );

        // Then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).name()).isEqualTo("Keyboard");
        assertThat(response.getContent().get(0).attributes()).containsEntry("color", "Black");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchAllCategories(eq(null), eq(ProductStatus.PUBLISHED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("name")).isNotNull();
        verifyNoInteractions(categoryService);
    }

    @Test
    void search_shouldSearchInsideCategoryAndDescendants_whenCategoryIdIsProvided() {
        // Given
        UUID categoryId = UUID.randomUUID();
        UUID childCategoryId = UUID.randomUUID();
        ProductSearchDocument document = document(UUID.randomUUID(), childCategoryId, "Mouse", "{\"color\":\"White\"}");

        when(categoryService.categoryAndDescendantIds(categoryId)).thenReturn(Set.of(categoryId, childCategoryId));
        when(repository.searchInCategories(eq("mouse"), eq(Set.of(categoryId, childCategoryId)), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        // When
        Page<ProductSearchResponse> response = productSearchService.search(
                " mouse ",
                categoryId,
                Map.of(),
                1,
                10,
                "price",
                Sort.Direction.ASC
        );

        // Then
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).productId()).isEqualTo(document.getProductId());
        verify(categoryService).categoryAndDescendantIds(categoryId);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchInCategories(eq("mouse"), eq(Set.of(categoryId, childCategoryId)), eq(ProductStatus.PUBLISHED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort().getOrderFor("price")).isNotNull();
    }

    @Test
    void search_shouldFilterDocumentsByAttributesAfterRepositorySearch() {
        // Given
        ProductSearchDocument blackKeyboard = document(UUID.randomUUID(), UUID.randomUUID(), "Black Keyboard", "{\"color\":\"Black\",\"layout\":\"TKL\"}");
        ProductSearchDocument whiteKeyboard = document(UUID.randomUUID(), UUID.randomUUID(), "White Keyboard", "{\"color\":\"White\",\"layout\":\"Full\"}");

        when(repository.searchAllCategories(eq("keyboard"), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(blackKeyboard, whiteKeyboard)));

        // When
        Page<ProductSearchResponse> response = productSearchService.search(
                "keyboard",
                null,
                Map.of("color", "black"),
                0,
                20,
                "name",
                Sort.Direction.ASC
        );

        // Then
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent()).extracting(ProductSearchResponse::name).containsExactly("Black Keyboard");
    }

    @Test
    void search_shouldReturnEmptyPage_whenAttributeFiltersDoNotMatchAnyDocument() {
        // Given
        ProductSearchDocument document = document(UUID.randomUUID(), UUID.randomUUID(), "Keyboard", "{\"color\":\"Black\"}");
        when(repository.searchAllCategories(eq("keyboard"), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        // When
        Page<ProductSearchResponse> response = productSearchService.search(
                "keyboard",
                null,
                Map.of("color", "White"),
                0,
                20,
                "name",
                Sort.Direction.ASC
        );

        // Then
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    @Test
    void search_shouldThrowIllegalStateException_whenProjectionAttributesJsonIsInvalid() {
        // Given
        ProductSearchDocument document = document(UUID.randomUUID(), UUID.randomUUID(), "Keyboard", "not-json");
        when(repository.searchAllCategories(eq("keyboard"), eq(ProductStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        // When / Then
        assertThatThrownBy(() -> productSearchService.search(
                "keyboard",
                null,
                Map.of(),
                0,
                20,
                "name",
                Sort.Direction.ASC
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read search attributes");
    }

    private ProductSearchDocument document(UUID productId, UUID categoryId, String name, String attributesJson) {
        ProductSearchDocument document = new ProductSearchDocument();
        document.setProductId(productId);
        document.setSellerId(UUID.randomUUID());
        document.setCategoryId(categoryId);
        document.setCategoryName("Keyboards");
        document.setName(name);
        document.setDescription(name + " description");
        document.setSku("SKU-" + productId.toString().substring(0, 8));
        document.setPrice(new BigDecimal("49.99"));
        document.setCurrency(CurrencyCode.INR);
        document.setStatus(ProductStatus.PUBLISHED);
        document.setAttributesJson(attributesJson);
        document.setTotalAvailableQuantity(25L);
        return document;
    }
}
