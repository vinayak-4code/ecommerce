package com.acme.ecommerce.catalog.validation;

import com.acme.ecommerce.catalog.dto.AttributeValueRequest;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit specification for seller product attribute validation.
 *
 * <p>These tests cover the category metadata contract without testing request
 * DTO annotations or the product mapper.</p>
 */
class ProductAttributeValidatorTest {

    private final ProductAttributeValidator validator = new ProductAttributeValidator();

    @Test
    void validate_shouldPass_whenAllRequiredAttributesArePresentAndValid() {
        // Given
        List<CategoryAttributeDefinition> definitions = List.of(
                create("ram",
                        AttributeType.SELECT,
                        true,
                        null,
                        null,
                        null,
                        null,
                        "8GB,16GB"),
                create("waterproof",
                        AttributeType.BOOLEAN,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
        );
        List<AttributeValueRequest> attributes = List.of(
                new AttributeValueRequest("ram", "16GB"),
                new AttributeValueRequest("waterproof", "true")
        );

        // When
        validator.validate(definitions, attributes);

        // Then
        // No exception means the product attributes satisfy the category metadata.
    }

    @Test
    void validate_shouldThrowBusinessException_whenRequiredAttributeIsMissing() {
        // Given
        List<CategoryAttributeDefinition> definitions = List.of(create("ram",
                AttributeType.STRING,
                true,
                null,
                null,
                null,
                null,
                null));

        // When / Then
        assertThatThrownBy(() -> validator.validate(definitions, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Missing required attribute: ram");
    }

    @Test
    void validate_shouldThrowBusinessException_whenUnknownAttributeIsSupplied() {
        // Given
        List<CategoryAttributeDefinition> definitions = List.of(
                create("ram",
                        AttributeType.STRING,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
        );
        List<AttributeValueRequest> attributes = List.of(new AttributeValueRequest("screen_size", "6.1"));

        // When / Then
        assertThatThrownBy(() -> validator.validate(definitions, attributes))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid attribute for category");
    }

    @Test
    void validate_shouldThrowBusinessException_whenStringAttributeIsOutsideLengthBounds() {
        // Given
        List<CategoryAttributeDefinition> definitions = List.of(create("model", AttributeType.STRING, true, 3, 5, null, null, null));

        // When / Then
        assertThatThrownBy(() -> validator.validate(definitions, List.of(new AttributeValueRequest("model", "AB"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shorter than minimum length");
        assertThatThrownBy(() -> validator.validate(definitions, List.of(new AttributeValueRequest("model", "ABCDEFG"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds maximum length");
    }

    @Test
    void validate_shouldThrowBusinessException_whenSelectValueIsNotAllowed() {
        // Given
        List<CategoryAttributeDefinition> definitions = List.of(create("color", AttributeType.SELECT, true, null, null, null, null, "Red,Blue"));

        // When / Then
        assertThatThrownBy(() -> validator.validate(definitions, List.of(new AttributeValueRequest("color", "Green"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be one of");
    }

    @Test
    void validate_shouldThrowBusinessException_whenNumberIsInvalidOrOutsideBounds() {
        // Given
        List<CategoryAttributeDefinition> definitions = List.of(create("weight", AttributeType.DECIMAL, true, null, null, new BigDecimal("1.00"), new BigDecimal("10.00"), null));

        // When / Then
        assertThatThrownBy(() -> validator.validate(definitions, List.of(new AttributeValueRequest("weight", "abc"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be numeric");
        assertThatThrownBy(() -> validator.validate(definitions, List.of(new AttributeValueRequest("weight", "0.50"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("below minimum value");
        assertThatThrownBy(() -> validator.validate(definitions, List.of(new AttributeValueRequest("weight", "15.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds maximum value");
    }

    @Test
    void validate_shouldThrowBusinessException_whenBooleanValueIsNotTrueOrFalse() {
        // Given
        List<CategoryAttributeDefinition> definitions = List.of(create("waterproof", AttributeType.BOOLEAN, true, null, null, null, null, null));

        // When / Then
        assertThatThrownBy(() -> validator.validate(definitions, List.of(new AttributeValueRequest("waterproof", "yes"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must be true or false");
    }

    private CategoryAttributeDefinition create(
            String code,
            AttributeType type,
            boolean required,
            Integer minLength,
            Integer maxLength,
            BigDecimal minValue,
            BigDecimal maxValue,
            String allowedValues
    ) {
        CategoryAttributeDefinition definition = new CategoryAttributeDefinition();
        definition.setCode(code);
        definition.setName(code);
        definition.setLabelText(code);
        definition.setAttributeType(type);
        definition.setRequired(required);
        definition.setMinLength(minLength);
        definition.setMaxLength(maxLength);
        definition.setMinValue(minValue);
        definition.setMaxValue(maxValue);
        definition.setAllowedValues(allowedValues);
        return definition;
    }
}
