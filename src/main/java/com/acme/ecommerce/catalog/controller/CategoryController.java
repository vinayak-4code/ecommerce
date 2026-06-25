package com.acme.ecommerce.catalog.controller;

import com.acme.ecommerce.catalog.dto.AttributeDefinitionRequest;
import com.acme.ecommerce.catalog.dto.AttributeDefinitionResponse;
import com.acme.ecommerce.catalog.dto.CategoryResponse;
import com.acme.ecommerce.catalog.dto.CreateCategoryRequest;
import com.acme.ecommerce.catalog.dto.UpdateCategoryRequest;
import com.acme.ecommerce.catalog.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Product-admin category API plus public category browsing.
 * Example: create Electronics as a parent, then Mobile Phones as a child with
 * mandatory RAM/Storage attributes and optional Battery Capacity.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    /**
     * Creates a category or sub-classification with predefined mandatory/optional attributes.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.create(request);
    }

    /**
     * Updates category name, parent, active flag, and upserts predefined attribute definitions.
     */
    @PutMapping("/{categoryId}")
    public CategoryResponse update(@PathVariable UUID categoryId, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(categoryId, request);
    }

    /**
     * Adds one predefined attribute definition to an existing category.
     */
    @PostMapping("/{categoryId}/attributes")
    @ResponseStatus(HttpStatus.CREATED)
    public AttributeDefinitionResponse addAttribute(
            @PathVariable UUID categoryId,
            @Valid @RequestBody AttributeDefinitionRequest request
    ) {
        return categoryService.addAttribute(categoryId, request);
    }

    /**
     * Lists categories with pagination for admin screens and customer category filters.
     */
    @GetMapping
    public Page<CategoryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return categoryService.list(page, size);
    }

    /**
     * Fetches category details including mandatory and optional attributes.
     */
    @GetMapping("/{categoryId}")
    public CategoryResponse get(@PathVariable UUID categoryId) {
        return categoryService.get(categoryId);
    }
}
