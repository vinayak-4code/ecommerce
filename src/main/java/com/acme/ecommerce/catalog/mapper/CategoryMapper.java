package com.acme.ecommerce.catalog.mapper;

import com.acme.ecommerce.catalog.dto.AttributeDefinitionResponse;
import com.acme.ecommerce.catalog.dto.CategoryResponse;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategoryMapper {
    public CategoryResponse toResponse(Category category, List<CategoryAttributeDefinition> attributes) {
        return new CategoryResponse(
                category.getId(),
                category.getParent() == null ? null : category.getParent().getId(),
                category.getName(),
                category.getSlug(),
                category.isActive(),
                attributes.stream().map(this::toAttributeResponse).toList()
        );
    }

    public AttributeDefinitionResponse toAttributeResponse(CategoryAttributeDefinition attribute) {
        return new AttributeDefinitionResponse(
                attribute.getId(),
                attribute.getName(),
                attribute.getCode(),
                attribute.getLabelText(),
                attribute.getAttributeType(),
                attribute.isRequired(),
                attribute.isSearchable(),
                attribute.isVisibleToCustomer(),
                attribute.getMinLength(),
                attribute.getMaxLength(),
                attribute.getMinValue(),
                attribute.getMaxValue(),
                attribute.getAllowedValues()
        );
    }
}
