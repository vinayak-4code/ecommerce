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
 * Product Admin category service for hierarchical classifications and attributes.
 *
 * <p>Categories are curated by Product Admin. Attributes are intentionally
 * attachable only to leaf categories so seller product forms remain accurate:
 * a seller cannot create a product under a generic root such as Electronics;
 * they must choose Mobile Phones, Laptops, or another final classification.</p>
 */
@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryAttributeDefinitionRepository attributeDefinitionRepository;
    private final CategoryMapper categoryMapper;

    /** Creates a root category or child classification. */
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

    /** Updates category metadata and replaces/upserts attribute metadata when supplied. */
    @Transactional
    public CategoryResponse update(UUID categoryId, UpdateCategoryRequest request) {
        Category category = requireCategory(categoryId);
        String slug = slugify(request.name());
        categoryRepository.findBySlug(slug)
                .filter(existing -> !existing.getId().equals(categoryId))
                .ifPresent(existing -> { throw new DuplicateResourceException("Category already exists with slug: " + slug); });
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

    /** Adds one predefined attribute definition to an existing leaf category. */
    @Transactional
    public AttributeDefinitionResponse addAttribute(UUID categoryId, AttributeDefinitionRequest request) {
        Category category = requireCategory(categoryId);
        ensureLeafCategory(category);
        attributeDefinitionRepository.findByCategoryIdAndCode(categoryId, normalizeCode(request.code()))
                .ifPresent(existing -> { throw new DuplicateResourceException("Attribute code already exists for category"); });
        CategoryAttributeDefinition attribute = toAttributeDefinition(category, request);
        return categoryMapper.toAttributeResponse(attributeDefinitionRepository.save(attribute));
    }

    /** Lists active categories with bounded pagination. */
    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.ASC, "name"));
        return categoryRepository.findByActiveTrue(pageable)
                .map(category -> categoryMapper.toResponse(category, attributeDefinitionRepository.findByCategoryId(category.getId())));
    }

    /** Returns all active categories as a flat tree-ready list for UI navigation. */
    @Transactional(readOnly = true)
    public List<CategoryResponse> tree() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .filter(Category::isActive)
                .map(category -> categoryMapper.toResponse(category, attributeDefinitionRepository.findByCategoryId(category.getId())))
                .toList();
    }

    /** Returns one category with its attribute definitions. */
    @Transactional(readOnly = true)
    public CategoryResponse get(UUID categoryId) {
        Category category = requireCategory(categoryId);
        return categoryMapper.toResponse(category, attributeDefinitionRepository.findByCategoryId(categoryId));
    }

    /** Loads a category entity or raises a not-found exception. */
    @Transactional(readOnly = true)
    public Category requireCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    /** Ensures product creation happens only under a final category. */
    @Transactional(readOnly = true)
    public void ensureLeafCategory(Category category) {
        boolean hasChildren = categoryRepository.findAll().stream()
                .anyMatch(candidate -> candidate.getParent() != null && candidate.getParent().getId().equals(category.getId()) && candidate.isActive());
        if (hasChildren) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Attributes/products can be attached only to a final sub-category");
        }
    }

    /** Returns a category plus its ancestors for coupon eligibility checks. */
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

    /** Returns selected category and all active descendants for public browsing. */
    @Transactional(readOnly = true)
    public Set<UUID> categoryAndDescendantIds(UUID categoryId) {
        Set<UUID> ids = new LinkedHashSet<>();
        collectDescendants(categoryId, ids);
        return ids;
    }

    private void collectDescendants(UUID categoryId, Set<UUID> ids) {
        ids.add(categoryId);
        categoryRepository.findAll().stream()
                .filter(category -> category.getParent() != null && category.getParent().getId().equals(categoryId) && category.isActive())
                .forEach(child -> collectDescendants(child.getId(), ids));
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
        ensureLeafCategory(category);
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
            applyAttributeFields(definition, request);
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
        definition.setCode(normalizeCode(request.code()));
        applyAttributeFields(definition, request);
        return definition;
    }

    private void applyAttributeFields(CategoryAttributeDefinition definition, AttributeDefinitionRequest request) {
        definition.setName(request.name().trim());
        definition.setLabelText((request.labelText() == null || request.labelText().isBlank()) ? request.name().trim() : request.labelText().trim());
        definition.setAttributeType(request.attributeType());
        definition.setRequired(request.required());
        definition.setSearchable(request.searchable());
        definition.setVisibleToCustomer(request.visibleToCustomer());
        definition.setMinLength(request.minLength());
        definition.setMaxLength(request.maxLength());
        definition.setMinValue(request.minValue());
        definition.setMaxValue(request.maxValue());
        definition.setAllowedValues(request.allowedValues() == null || request.allowedValues().isBlank() ? null : request.allowedValues().trim());
    }

    private String slugify(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }
}
