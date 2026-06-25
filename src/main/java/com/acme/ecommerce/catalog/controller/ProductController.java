package com.acme.ecommerce.catalog.controller;

import com.acme.ecommerce.catalog.dto.CreateProductRequest;
import com.acme.ecommerce.catalog.dto.ProductResponse;
import com.acme.ecommerce.catalog.dto.ProductVersionResponse;
import com.acme.ecommerce.catalog.dto.UpdateProductRequest;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Seller product-management API. Products are stored in PostgreSQL as the
 * source of truth and projected to search after create/update/publish events.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    /**
     * Creates a seller-owned draft product after validating category-specific mandatory attributes.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(CurrentUser.require().userId(), request);
    }

    /**
     * Updates a seller-owned product and captures a version snapshot before the change.
     */
    @PutMapping("/{productId}")
    public ProductResponse update(@PathVariable UUID productId, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(CurrentUser.require().userId(), productId, request);
    }

    /**
     * Returns product details from the transactional PostgreSQL catalog.
     */
    @GetMapping("/{productId}")
    public ProductResponse get(@PathVariable UUID productId) {
        return productService.get(productId);
    }

    /**
     * Publishes a seller-owned product and updates the search projection.
     */
    @PatchMapping("/{productId}/publish")
    public ProductResponse publish(@PathVariable UUID productId) {
        return productService.publish(CurrentUser.require().userId(), productId);
    }

    /**
     * Unpublishes a seller-owned product without deleting its history.
     */
    @PatchMapping("/{productId}/unpublish")
    public ProductResponse unpublish(@PathVariable UUID productId) {
        return productService.unpublish(CurrentUser.require().userId(), productId);
    }

    /**
     * Soft-deletes a seller-owned product and removes it from search results.
     */
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID productId) {
        productService.delete(CurrentUser.require().userId(), productId);
    }

    /**
     * Lists the latest product versions so a seller can audit product changes.
     */
    @GetMapping("/{productId}/versions")
    public List<ProductVersionResponse> versions(@PathVariable UUID productId) {
        return productService.versions(CurrentUser.require().userId(), productId);
    }

    /**
     * Restores product details and attributes from a previous captured version.
     */
    @PostMapping("/{productId}/rollback/{versionNumber}")
    public ProductResponse rollback(@PathVariable UUID productId, @PathVariable int versionNumber) {
        return productService.rollback(CurrentUser.require().userId(), productId, versionNumber);
    }
}
