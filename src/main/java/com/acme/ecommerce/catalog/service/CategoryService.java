package com.acme.ecommerce.catalog.service;

import com.acme.ecommerce.catalog.dto.AttributeDefinitionRequest;
import com.acme.ecommerce.catalog.dto.AttributeDefinitionResponse;
import com.acme.ecommerce.catalog.dto.CategoryResponse;
import com.acme.ecommerce.catalog.dto.CreateCategoryRequest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.mapper.CategoryMapper;
import com.acme.ecommerce.catalog.repository.CategoryAttributeDefinitionRepository;
import com.acme.ecommerce.catalog.repository.CategoryRepository;
import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final CategoryAttributeDefinitionRepository attributeDefinitionRepository;
    private final CategoryMapper categoryMapper;

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
        List<CategoryAttributeDefinition> attributes = saveAttributes(saved, request.attributes());
        return categoryMapper.toResponse(saved, attributes);
    }

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

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(category -> categoryMapper.toResponse(category, attributeDefinitionRepository.findByCategoryId(category.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID categoryId) {
        Category category = requireCategory(categoryId);
        return categoryMapper.toResponse(category, attributeDefinitionRepository.findByCategoryId(categoryId));
    }

    @Transactional(readOnly = true)
    public Category requireCategory(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private List<CategoryAttributeDefinition> saveAttributes(Category category, List<AttributeDefinitionRequest> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }
        List<CategoryAttributeDefinition> definitions = attributes.stream()
                .map(request -> toAttributeDefinition(category, request))
                .toList();
        return attributeDefinitionRepository.saveAll(definitions);
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
