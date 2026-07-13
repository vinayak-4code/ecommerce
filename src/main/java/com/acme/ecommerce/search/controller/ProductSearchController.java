package com.acme.ecommerce.search.controller;

import com.acme.ecommerce.search.dto.ProductSearchResponse;
import com.acme.ecommerce.search.service.ProductSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Public product listing/search API backed by the denormalized search projection.
 * The projection is stored in PostgreSQL for the demo and can be moved to OpenSearch.
 */
@RestController
@RequestMapping("/api/v1/search/products")
@RequiredArgsConstructor
public class ProductSearchController {
    private static final String ATTRIBUTE_PREFIX = "attr_";

    private final ProductSearchService productSearchService;

    /**
     * Searches the denormalized product projection using text, category, attributes, pagination, and sorting.
     */
    @GetMapping
    public Page<ProductSearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction,
            @RequestParam Map<String, String> requestParams
    ) {
        Map<String, String> attributes = requestParams.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(ATTRIBUTE_PREFIX))
                .collect(Collectors.toMap(entry -> entry.getKey().substring(ATTRIBUTE_PREFIX.length()), Map.Entry::getValue));
        return productSearchService.search(q, categoryId, attributes, page, size, sortBy, direction);
    }
}
