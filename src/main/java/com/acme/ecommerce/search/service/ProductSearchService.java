package com.acme.ecommerce.search.service;

import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.service.CategoryService;
import com.acme.ecommerce.search.dto.ProductSearchResponse;
import com.acme.ecommerce.search.entity.ProductSearchDocument;
import com.acme.ecommerce.search.repository.ProductSearchDocumentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Product search query service backed by the denormalized search projection.
 *
 * <p>The demo stores this projection in PostgreSQL for portability, but the
 * service boundary mirrors an OpenSearch adapter: text query, category tree
 * filter, attribute filters, pagination, and sorting all flow through here.</p>
 */
@Service
@RequiredArgsConstructor
public class ProductSearchService {
    private final ProductSearchDocumentRepository repository;
    private final CategoryService categoryService;
    private final ObjectMapper objectMapper;

    /** Searches published products from the projection using optional filters. */
    @Transactional(readOnly = true)
    public Page<ProductSearchResponse> search(
            String query,
            UUID categoryId,
            Map<String, String> attributeFilters,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(direction, safeSort(sortBy)));
        Page<ProductSearchDocument> documents;
        if (categoryId == null) {
            documents = repository.searchAllCategories(emptyToNull(query), ProductStatus.PUBLISHED, pageable);
        } else {
            Set<UUID> categoryIds = categoryService.categoryAndDescendantIds(categoryId);
            documents = repository.searchInCategories(emptyToNull(query), categoryIds, ProductStatus.PUBLISHED, pageable);
        }
        if (attributeFilters == null || attributeFilters.isEmpty()) {
            return documents.map(this::toResponse);
        }
        java.util.List<ProductSearchResponse> filtered = documents.stream()
                .map(this::toResponse)
                .filter(response -> matchesAttributes(response.attributes(), attributeFilters))
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    private String safeSort(String sortBy) {
        String requestedSort = sortBy == null || sortBy.isBlank() ? "name" : sortBy;
        return switch (requestedSort) {
            case "price", "name", "updatedAt", "totalAvailableQuantity" -> requestedSort;
            default -> "name";
        };
    }

    private boolean matchesAttributes(Map<String, String> attributes, Map<String, String> filters) {
        return filters.entrySet().stream()
                .allMatch(entry -> entry.getValue().equalsIgnoreCase(attributes.get(entry.getKey())));
    }

    private ProductSearchResponse toResponse(ProductSearchDocument document) {
        return new ProductSearchResponse(
                document.getProductId(),
                document.getSellerId(),
                document.getCategoryId(),
                document.getCategoryName(),
                document.getName(),
                document.getDescription(),
                document.getSku(),
                document.getPrice(),
                document.getCurrency(),
                document.getStatus(),
                readAttributes(document.getAttributesJson()),
                document.getTotalAvailableQuantity()
        );
    }

    private Map<String, String> readAttributes(String attributesJson) {
        try {
            return objectMapper.readValue(attributesJson, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read search attributes", exception);
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
