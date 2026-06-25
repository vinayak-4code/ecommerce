package com.acme.ecommerce.catalog.validation;

import com.acme.ecommerce.catalog.dto.AttributeValueRequest;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            validateType(definition.getCode(), definition.getAttributeType(), attribute.value());
        }
    }

    private void validateType(String code, AttributeType type, String value) {
        try {
            switch (type) {
                case STRING -> {
                    if (value.isBlank()) {
                        fail("Attribute " + code + " cannot be blank");
                    }
                }
                case NUMBER -> new BigDecimal(value);
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        fail("Attribute " + code + " must be true or false");
                    }
                }
            }
        } catch (NumberFormatException exception) {
            fail("Attribute " + code + " must be numeric");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
    }

    private void fail(String message) {
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }
}
