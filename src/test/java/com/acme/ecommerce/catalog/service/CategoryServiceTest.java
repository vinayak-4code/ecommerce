package com.acme.ecommerce.catalog.service;

import com.acme.ecommerce.catalog.dto.AttributeDefinitionRequest;
import com.acme.ecommerce.catalog.dto.AttributeDefinitionResponse;
import com.acme.ecommerce.catalog.dto.CategoryResponse;
import com.acme.ecommerce.catalog.dto.CreateCategoryRequest;
import com.acme.ecommerce.catalog.dto.UpdateCategoryRequest;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.catalog.mapper.CategoryMapper;
import com.acme.ecommerce.catalog.repository.CategoryAttributeDefinitionRepository;
import com.acme.ecommerce.catalog.repository.CategoryRepository;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for product-admin category behavior.
 *
 * <p>The service owns slug uniqueness, leaf-category attribute rules, cycle
 * protection, and category tree traversal. Mapping output itself is mocked.</p>
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryAttributeDefinitionRepository attributeDefinitionRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void create_shouldPersistRootCategoryAndReturnMappedResponse_whenSlugIsUnique() {
        // Given
        UUID categoryId = UUID.randomUUID();
        CreateCategoryRequest request = new CreateCategoryRequest(null, " Electronics ", null);
        CategoryResponse expectedResponse = categoryResponse(categoryId, "Electronics");

        when(categoryRepository.findBySlug("electronics")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(categoryId);
            return category;
        });
        when(attributeDefinitionRepository.findByCategoryId(categoryId)).thenReturn(List.of());
        when(categoryMapper.toResponse(any(Category.class), eq(List.of()))).thenReturn(expectedResponse);

        // When
        CategoryResponse response = categoryService.create(request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(categoryRepository).save(any(Category.class));
        verify(attributeDefinitionRepository).findByCategoryId(categoryId);
    }

    @Test
    void create_shouldThrowDuplicateResourceException_whenSlugAlreadyExists() {
        // Given
        CreateCategoryRequest request = new CreateCategoryRequest(null, "Electronics", null);
        when(categoryRepository.findBySlug("electronics")).thenReturn(Optional.of(category(UUID.randomUUID(), "Electronics", null)));

        // When / Then
        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Category already exists with slug");
    }

    @Test
    void update_shouldRejectCycle_whenProposedParentIsSameCategory() {
        // Given
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, "Electronics", null);
        UpdateCategoryRequest request = new UpdateCategoryRequest(categoryId, "Electronics", true, null);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySlug("electronics")).thenReturn(Optional.of(category));

        // When / Then
        assertThatThrownBy(() -> categoryService.update(categoryId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Category cannot be assigned to itself or its descendant");
    }

    @Test
    void addAttribute_shouldPersistAttribute_whenCategoryIsLeafAndCodeIsUnique() {
        // Given
        UUID categoryId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        Category category = category(categoryId, "Mobile Phones", null);
        AttributeDefinitionRequest request = attributeRequest("RAM", "ram");
        AttributeDefinitionResponse expectedResponse = attributeResponse(attributeId, "ram");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(attributeDefinitionRepository.findByCategoryIdAndCode(categoryId, "ram")).thenReturn(Optional.empty());
        when(attributeDefinitionRepository.save(any(CategoryAttributeDefinition.class))).thenAnswer(invocation -> {
            CategoryAttributeDefinition definition = invocation.getArgument(0);
            definition.setId(attributeId);
            return definition;
        });
        when(categoryMapper.toAttributeResponse(any(CategoryAttributeDefinition.class))).thenReturn(expectedResponse);

        // When
        AttributeDefinitionResponse response = categoryService.addAttribute(categoryId, request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(attributeDefinitionRepository).save(any(CategoryAttributeDefinition.class));
    }

    @Test
    void addAttribute_shouldThrowDuplicateResourceException_whenCodeAlreadyExistsForCategory() {
        // Given
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, "Mobile Phones", null);
        CategoryAttributeDefinition existing = definition(category, "ram", true);
        AttributeDefinitionRequest request = attributeRequest("RAM", "ram");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findAll()).thenReturn(List.of(category));
        when(attributeDefinitionRepository.findByCategoryIdAndCode(categoryId, "ram")).thenReturn(Optional.of(existing));

        // When / Then
        assertThatThrownBy(() -> categoryService.addAttribute(categoryId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Attribute code already exists");
    }

    @Test
    void ensureLeafCategory_shouldThrowBusinessException_whenActiveChildExists() {
        // Given
        Category parent = category(UUID.randomUUID(), "Electronics", null);
        Category activeChild = category(UUID.randomUUID(), "Mobile Phones", parent);
        when(categoryRepository.findAll()).thenReturn(List.of(parent, activeChild));

        // When / Then
        assertThatThrownBy(() -> categoryService.ensureLeafCategory(parent))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only to a final sub-category");
    }

    @Test
    void categoryAndAncestorIds_shouldReturnCategoryThenAncestors_whenParentHierarchyExists() {
        // Given
        Category root = category(UUID.randomUUID(), "Electronics", null);
        Category child = category(UUID.randomUUID(), "Mobile Phones", root);
        Category leaf = category(UUID.randomUUID(), "Android Phones", child);
        when(categoryRepository.findById(leaf.getId())).thenReturn(Optional.of(leaf));

        // When
        Set<UUID> ids = categoryService.categoryAndAncestorIds(leaf.getId());

        // Then
        assertThat(ids).containsExactly(leaf.getId(), child.getId(), root.getId());
    }

    @Test
    void categoryAndDescendantIds_shouldReturnOnlyActiveDescendants_whenTreeContainsInactiveChild() {
        // Given
        Category root = category(UUID.randomUUID(), "Electronics", null);
        Category activeChild = category(UUID.randomUUID(), "Mobile Phones", root);
        Category inactiveChild = category(UUID.randomUUID(), "Old Phones", root);
        inactiveChild.setActive(false);
        when(categoryRepository.findAll()).thenReturn(List.of(root, activeChild, inactiveChild));

        // When
        Set<UUID> ids = categoryService.categoryAndDescendantIds(root.getId());

        // Then
        assertThat(ids).containsExactly(root.getId(), activeChild.getId());
    }

    @Test
    void list_shouldBoundPaginationAndSortByName_whenRequestedPageAndSizeAreOutOfRange() {
        // Given
        Category category = category(UUID.randomUUID(), "Electronics", null);
        CategoryResponse mapped = categoryResponse(category.getId(), "Electronics");
        when(categoryRepository.findByActiveTrue(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(category)));
        when(attributeDefinitionRepository.findByCategoryId(category.getId())).thenReturn(List.of());
        when(categoryMapper.toResponse(category, List.of())).thenReturn(mapped);

        // When
        Page<CategoryResponse> response = categoryService.list(-10, 500);

        // Then
        assertThat(response.getContent()).containsExactly(mapped);
        verify(categoryRepository).findByActiveTrue(any(Pageable.class));
    }

    @Test
    void requireCategory_shouldThrowResourceNotFoundException_whenCategoryDoesNotExist() {
        // Given
        UUID categoryId = UUID.randomUUID();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> categoryService.requireCategory(categoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }


    @Test
    void update_shouldPersistMetadataAndReuseExistingAttributes_whenAttributesAreNotProvided() {
        // Given
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, "Old Name", null);
        CategoryAttributeDefinition existingAttribute = definition(category, "ram", true);
        CategoryResponse expectedResponse = categoryResponse(categoryId, "New Name");
        UpdateCategoryRequest request = new UpdateCategoryRequest(null, " New Name ", false, null);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySlug("new-name")).thenReturn(Optional.empty());
        when(categoryRepository.save(category)).thenReturn(category);
        when(attributeDefinitionRepository.findByCategoryId(categoryId)).thenReturn(List.of(existingAttribute));
        when(categoryMapper.toResponse(category, List.of(existingAttribute))).thenReturn(expectedResponse);

        // When
        CategoryResponse response = categoryService.update(categoryId, request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        assertThat(category.getName()).isEqualTo("New Name");
        assertThat(category.getSlug()).isEqualTo("new-name");
        assertThat(category.isActive()).isFalse();
        assertThat(category.getParent()).isNull();
    }

    @Test
    void get_shouldReturnMappedCategoryWithAttributes_whenCategoryExists() {
        // Given
        UUID categoryId = UUID.randomUUID();
        Category category = category(categoryId, "Electronics", null);
        CategoryAttributeDefinition attribute = definition(category, "brand", false);
        CategoryResponse expectedResponse = categoryResponse(categoryId, "Electronics");

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(attributeDefinitionRepository.findByCategoryId(categoryId)).thenReturn(List.of(attribute));
        when(categoryMapper.toResponse(category, List.of(attribute))).thenReturn(expectedResponse);

        // When
        CategoryResponse response = categoryService.get(categoryId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(categoryMapper).toResponse(category, List.of(attribute));
    }

    @Test
    void tree_shouldReturnOnlyActiveCategoriesSortedByNameFromRepositoryResult() {
        // Given
        Category active = category(UUID.randomUUID(), "Active", null);
        Category inactive = category(UUID.randomUUID(), "Inactive", null);
        inactive.setActive(false);
        CategoryResponse expectedResponse = categoryResponse(active.getId(), "Active");

        when(categoryRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "name")))
                .thenReturn(List.of(active, inactive));
        when(attributeDefinitionRepository.findByCategoryId(active.getId())).thenReturn(List.of());
        when(categoryMapper.toResponse(active, List.of())).thenReturn(expectedResponse);

        // When
        List<CategoryResponse> responses = categoryService.tree();

        // Then
        assertThat(responses).containsExactly(expectedResponse);
    }

    private AttributeDefinitionRequest attributeRequest(String name, String code) {
        return new AttributeDefinitionRequest(name, code, name, AttributeType.STRING, true, true, true, 1, 80, null, null, null);
    }

    private AttributeDefinitionResponse attributeResponse(UUID id, String code) {
        return new AttributeDefinitionResponse(id, code, code, code, AttributeType.STRING, true, true, true, 1, 80, null, null, null);
    }

    private CategoryResponse categoryResponse(UUID id, String name) {
        return new CategoryResponse(id, null, name, name.toLowerCase(), true, List.of());
    }

    private Category category(UUID id, String name, Category parent) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setSlug(name.toLowerCase().replace(" ", "-"));
        category.setParent(parent);
        category.setActive(true);
        return category;
    }

    private CategoryAttributeDefinition definition(Category category, String code, boolean required) {
        CategoryAttributeDefinition definition = new CategoryAttributeDefinition();
        definition.setId(UUID.randomUUID());
        definition.setCategory(category);
        definition.setCode(code);
        definition.setName(code);
        definition.setLabelText(code);
        definition.setAttributeType(AttributeType.STRING);
        definition.setRequired(required);
        return definition;
    }
}
