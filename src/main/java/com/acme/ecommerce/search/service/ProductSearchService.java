package com.acme.ecommerce.search.service;

import com.acme.ecommerce.catalog.enums.ProductStatus;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductSearchService {
    private final ProductSearchDocumentRepository repository;
    private final ObjectMapper objectMapper;

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
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        Page<ProductSearchDocument> documents = repository.search(emptyToNull(query), categoryId, ProductStatus.PUBLISHED, pageable);
        if (attributeFilters == null || attributeFilters.isEmpty()) {
            return documents.map(this::toResponse);
        }
        java.util.List<ProductSearchResponse> filtered = documents.stream()
                .map(this::toResponse)
                .filter(response -> matchesAttributes(response.attributes(), attributeFilters))
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
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
            return objectMapper.readValue(attributesJson, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read search attributes", exception);
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
