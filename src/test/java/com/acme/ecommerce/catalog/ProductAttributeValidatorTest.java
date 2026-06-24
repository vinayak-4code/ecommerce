package com.acme.ecommerce.catalog;

import com.acme.ecommerce.catalog.dto.AttributeValueRequest;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.catalog.validation.ProductAttributeValidator;
import com.acme.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAttributeValidatorTest {
    private final ProductAttributeValidator validator = new ProductAttributeValidator();

    @Test
    void rejectsMissingRequiredAttribute() {
        CategoryAttributeDefinition ram = definition("ram", AttributeType.STRING, true);

        assertThatThrownBy(() -> validator.validate(List.of(ram), List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing required attribute");
    }

    @Test
    void acceptsValidCategorySpecificAttributes() {
        CategoryAttributeDefinition ram = definition("ram", AttributeType.STRING, true);
        CategoryAttributeDefinition storage = definition("storage", AttributeType.STRING, true);

        assertThatCode(() -> validator.validate(
                List.of(ram, storage),
                List.of(new AttributeValueRequest("ram", "8GB"), new AttributeValueRequest("storage", "128GB"))
        )).doesNotThrowAnyException();
    }

    private CategoryAttributeDefinition definition(String code, AttributeType type, boolean required) {
        CategoryAttributeDefinition definition = new CategoryAttributeDefinition();
        definition.setCode(code);
        definition.setName(code);
        definition.setAttributeType(type);
        definition.setRequired(required);
        return definition;
    }
}
