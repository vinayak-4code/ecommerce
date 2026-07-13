package com.acme.ecommerce.catalog.validation;

import com.acme.ecommerce.catalog.dto.AttributeValueRequest;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Validates seller-supplied product attribute values against Product Admin metadata. */
@Component
public class ProductAttributeValidator {
    public void validate(List<CategoryAttributeDefinition> definitions, List<AttributeValueRequest> attributes) {
        List<AttributeValueRequest> safeAttributes = attributes == null ? List.of() : attributes;
        Map<String, CategoryAttributeDefinition> definitionsByCode = definitions.stream()
                .collect(Collectors.toMap(CategoryAttributeDefinition::getCode, Function.identity()));
        Set<String> suppliedCodes = safeAttributes.stream().map(AttributeValueRequest::code).map(this::normalizeCode).collect(Collectors.toSet());

        definitions.stream()
                .filter(CategoryAttributeDefinition::isRequired)
                .filter(definition -> !suppliedCodes.contains(definition.getCode()))
                .findFirst()
                .ifPresent(definition -> fail("Missing required attribute: " + definition.getCode()));

        for (AttributeValueRequest attribute : safeAttributes) {
            CategoryAttributeDefinition definition = definitionsByCode.get(normalizeCode(attribute.code()));
            if (definition == null) {
                fail("Invalid attribute for category: " + attribute.code());
            }
            validateValue(definition, attribute.value());
        }
    }

    private void validateValue(CategoryAttributeDefinition definition, String value) {
        String code = definition.getCode();
        String safeValue = value == null ? "" : value.trim();
        if (safeValue.isBlank()) {
            if (definition.isRequired()) {
                fail("Attribute " + code + " cannot be blank");
            }
            return;
        }
        switch (definition.getAttributeType()) {
            case STRING -> validateLength(definition, safeValue);
            case SELECT -> validateAllowedValue(definition, safeValue);
            case MULTI_SELECT -> Arrays.stream(safeValue.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .forEach(item -> validateAllowedValue(definition, item));
            case NUMBER, DECIMAL -> validateNumber(definition, safeValue);
            case BOOLEAN -> {
                if (!"true".equalsIgnoreCase(safeValue) && !"false".equalsIgnoreCase(safeValue)) {
                    fail("Attribute " + code + " must be true or false");
                }
            }
        }
    }

    private void validateLength(CategoryAttributeDefinition definition, String value) {
        if (definition.getMinLength() != null && value.length() < definition.getMinLength()) {
            fail("Attribute " + definition.getCode() + " is shorter than minimum length");
        }
        if (definition.getMaxLength() != null && value.length() > definition.getMaxLength()) {
            fail("Attribute " + definition.getCode() + " exceeds maximum length");
        }
    }

    private void validateAllowedValue(CategoryAttributeDefinition definition, String value) {
        validateLength(definition, value);
        String allowedValues = definition.getAllowedValues();
        if (allowedValues == null || allowedValues.isBlank()) {
            return;
        }
        boolean allowed = Arrays.stream(allowedValues.split(","))
                .map(String::trim)
                .anyMatch(item -> item.equalsIgnoreCase(value));
        if (!allowed) {
            fail("Attribute " + definition.getCode() + " must be one of: " + allowedValues);
        }
    }

    private void validateNumber(CategoryAttributeDefinition definition, String value) {
        try {
            BigDecimal decimal = new BigDecimal(value);
            if (definition.getMinValue() != null && decimal.compareTo(definition.getMinValue()) < 0) {
                fail("Attribute " + definition.getCode() + " is below minimum value");
            }
            if (definition.getMaxValue() != null && decimal.compareTo(definition.getMaxValue()) > 0) {
                fail("Attribute " + definition.getCode() + " exceeds maximum value");
            }
        } catch (NumberFormatException exception) {
            fail("Attribute " + definition.getCode() + " must be numeric");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private void fail(String message) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
