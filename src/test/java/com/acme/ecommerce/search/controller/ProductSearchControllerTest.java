package com.acme.ecommerce.search.controller;

import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.search.dto.ProductSearchResponse;
import com.acme.ecommerce.search.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for public product search/listing controller.
 *
 * <p>The controller extracts attribute filters from request parameters using the
 * attr_ prefix and delegates the actual listing behavior to ProductSearchService.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductSearchControllerTest {

    @Mock
    private ProductSearchService productSearchService;

    @InjectMocks
    private ProductSearchController productSearchController;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_shouldExtractAttributeFiltersAndDelegateToProductSearchService() {
        // Given
        UUID categoryId = UUID.randomUUID();
        Map<String, String> requestParams = Map.of(
                "q", "keyboard",
                "attr_color", "Black",
                "attr_layout", "TKL",
                "page", "0",
                "size", "20"
        );
        Page<ProductSearchResponse> expectedPage = new PageImpl<>(List.of(productSearchResponse("Keyboard")));
        when(productSearchService.search(eq("keyboard"), eq(categoryId), org.mockito.ArgumentMatchers.anyMap(), eq(0), eq(20), eq("price"), eq(Sort.Direction.DESC)))
                .thenReturn(expectedPage);

        // When
        Page<ProductSearchResponse> response = productSearchController.search(
                "keyboard",
                categoryId,
                0,
                20,
                "price",
                Sort.Direction.DESC,
                requestParams
        );

        // Then
        assertThat(response).isSameAs(expectedPage);

        ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);
        verify(productSearchService).search(eq("keyboard"), eq(categoryId), attributes.capture(), eq(0), eq(20), eq("price"), eq(Sort.Direction.DESC));
        assertThat(attributes.getValue())
                .containsEntry("color", "Black")
                .containsEntry("layout", "TKL")
                .doesNotContainKeys("q", "page", "size");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void search_shouldPassEmptyAttributes_whenNoAttributeParametersArePresent() {
        // Given
        Map<String, String> requestParams = Map.of("q", "keyboard", "sortBy", "name");
        Page<ProductSearchResponse> expectedPage = new PageImpl<>(List.of(productSearchResponse("Keyboard")));
        when(productSearchService.search(eq("keyboard"), eq(null), org.mockito.ArgumentMatchers.anyMap(), eq(0), eq(20), eq("name"), eq(Sort.Direction.ASC)))
                .thenReturn(expectedPage);

        // When
        Page<ProductSearchResponse> response = productSearchController.search(
                "keyboard",
                null,
                0,
                20,
                "name",
                Sort.Direction.ASC,
                requestParams
        );

        // Then
        assertThat(response).isSameAs(expectedPage);

        ArgumentCaptor<Map> attributes = ArgumentCaptor.forClass(Map.class);
        verify(productSearchService).search(eq("keyboard"), eq(null), attributes.capture(), eq(0), eq(20), eq("name"), eq(Sort.Direction.ASC));
        assertThat(attributes.getValue()).isEmpty();
    }

    private ProductSearchResponse productSearchResponse(String name) {
        return new ProductSearchResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Keyboards",
                name,
                "Mechanical keyboard",
                "KB-100",
                new BigDecimal("49.99"),
                CurrencyCode.INR,
                ProductStatus.PUBLISHED,
                Map.of("color", "Black"),
                25L
        );
    }
}
