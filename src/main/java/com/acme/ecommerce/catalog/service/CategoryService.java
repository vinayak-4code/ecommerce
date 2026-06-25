package com.acme.ecommerce.catalog.service;

import com.acme.ecommerce.catalog.dto.AttributeDefinitionRequest;
import com.acme.ecommerce.catalog.dto.AttributeDefinitionResponse;
import com.acme.ecommerce.catalog.dto.CategoryResponse;
import com.acme.ecommerce.catalog.dto.CreateCategoryRequest;
import com.acme.ecommerce.catalog.dto.UpdateCategoryRequest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.mapper.CategoryMapper;
import com.acme.ecommerce.catalog.repository.CategoryAttributeDefinitionRepository;
import com.acme.ecommerce.catalog.repository.CategoryRepository;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Owns category hierarchy and predefined attribute definitions.
 *
 * <p>Only PRODUCT_ADMIN reaches write methods through Spring Security. Sellers
 * can then create products only with attributes that are already defined here.
 * Example: Mobile Phones may require RAM and Storage while Screen Size is optional.</p>
 */
/**
 * Product Admin category service for hierarchical classifications and attributes.
 *
 * <p>Categories are intentionally curated by Product Admin instead of sellers.
 * This keeps product attributes predefined and makes search filters predictable.
 * Example: Mobile Phones can require RAM and Storage while making Battery
 * Capacity optional.</p>
 */
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryAttributeDefinitionRepository attributeDefinitionRepository;
    private final CategoryMapper categoryMapper;

    /**
     * Creates a root category or child classification with optional attribute definitions.
     */
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String slug = slugify(request.name());
        if (categoryRepository.findBySlug(slug).isPresent()) {
            throw new DuplicateResourceException("Category already exists with slug: " + slug);
        }
        Category category = new Category();
        category.setName(request.name().trim());
        category.setSlug(slug);
        if (request.parentId() != null) {
            category.setParent(requireCategory(request.parentId()));
        }
        Category saved = categoryRepository.save(category);
        List<CategoryAttributeDefinition> attributes = upsertAttributes(saved, request.attributes());
        return categoryMapper.toResponse(saved, attributes);
    }

    /**
     * Updates category metadata and upserts incoming attribute definitions.
     */
    @Transactional
    public CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
        Category category = requireCategory(categoryId);
        String slug = slugify(request.name());
        categoryRepository.findBySlug(slug)
                .filter(existing -> !existing.getId().equals(categoryId))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Category already exists with slug: " + slug);
                });
        Category parent = request.parentId() == null ? null : requireCategory(request.parentId());
        ensureNoCycle(categoryId, parent);
        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setActive(request.active());
        category.setParent(parent);
        Category saved = categoryRepository.save(category);
        List<CategoryAttributeDefinition> attributes = request.attributes() == null
                ? attributeDefinitionRepository.findByCategoryId(categoryId)
                : upsertAttributes(saved, request.attributes());
        return categoryMapper.toResponse(saved, attributes);
    }

    /**
     * Adds a single predefined attribute to an existing category.
     */
    @Transactional
    public AttributeDefinitionResponse addAttribute(UUID categoryId, AttributeDefinitionRequest request) {
        Category category = requireCategory(categoryId);
        attributeDefinitionRepository.findByCategoryIdAndCode(categoryId, normalizeCode(request.code()))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Attribute code already exists for category");
                });
        CategoryAttributeDefinition attribute = toAttributeDefinition(category, request);
        return categoryMapper.toAttributeResponse(attributeDefinitionRepository.save(attribute));
    }

    /**
     * Lists categories with bounded pagination.
     */
    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "name"));
        return categoryRepository.findByActiveTrue(pageable)
                .map(category -> categoryMapper.toResponse(category, attributeDefinitionRepository.findByCategoryId(category.getId())));
    }

    /**
     * Returns one category with its attribute definitions.
     */
    @Transactional(readOnly = true)
    public CategoryResponse get(UUID categoryId) {
        Category category = requireCategory(categoryId);
        return categoryMapper.toResponse(category, attributeDefinitionRepository.findByCategoryId(categoryId));
    }

    /**
     * Loads a category entity or raises a not-found exception.
     */
    @Transactional(readOnly = true)
    public Category requireCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    /**
     * Returns a category plus its ancestors for category-tree coupon eligibility.
     */
    @Transactional(readOnly = true)
    public Set<UUID> categoryAndAncestorIds(UUID categoryId) {
        Set<UUID> ids = new LinkedHashSet<>();
        Category current = requireCategory(categoryId);
        while (current != null) {
            ids.add(current.getId());
            current = current.getParent();
        }
        return ids;
    }

    private void ensureNoCycle(UUID categoryId, Category proposedParent) {
        Category current = proposedParent;
        while (current != null) {
            if (current.getId().equals(categoryId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Category cannot be assigned to itself or its descendant");
            }
            current = current.getParent();
        }
    }

    private List<CategoryAttributeDefinition> upsertAttributes(Category category, List<AttributeDefinitionRequest> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return attributeDefinitionRepository.findByCategoryId(category.getId());
        }
        ensureNoDuplicateCodes(attributes);

        List<CategoryAttributeDefinition> existing = attributeDefinitionRepository.findByCategoryId(category.getId());
        List<CategoryAttributeDefinition> updatedDefinitions = new ArrayList<>();

        for (AttributeDefinitionRequest request : attributes) {
            String code = normalizeCode(request.code());
            CategoryAttributeDefinition definition = existing.stream()
                    .filter(candidate -> candidate.getCode().equals(code))
                    .findFirst()
                    .orElseGet(() -> {
                        CategoryAttributeDefinition created = new CategoryAttributeDefinition();
                        created.setCategory(category);
                        created.setCode(code);
                        return created;
                    });
            definition.setName(request.name().trim());
            definition.setAttributeType(request.attributeType());
            definition.setRequired(request.required());
            definition.setSearchable(request.searchable());
            updatedDefinitions.add(definition);
        }

        attributeDefinitionRepository.saveAll(updatedDefinitions);
        return attributeDefinitionRepository.findByCategoryId(category.getId());
    }

    private void ensureNoDuplicateCodes(List<AttributeDefinitionRequest> attributes) {
        Set<String> codes = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (AttributeDefinitionRequest attribute : attributes) {
            String code = normalizeCode(attribute.code());
            if (!codes.add(code)) {
                duplicates.add(code);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Duplicate category attribute codes: " + duplicates);
        }
    }

    private CategoryAttributeDefinition toAttributeDefinition(Category category, AttributeDefinitionRequest request) {
        CategoryAttributeDefinition definition = new CategoryAttributeDefinition();
        definition.setCategory(category);
        definition.setName(request.name().trim());
        definition.setCode(normalizeCode(request.code()));
        definition.setAttributeType(request.attributeType());
        definition.setRequired(request.required());
        definition.setSearchable(request.searchable());
        return definition;
    }

    private String slugify(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }
}
