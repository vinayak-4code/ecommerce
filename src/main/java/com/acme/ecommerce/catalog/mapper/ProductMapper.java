package com.acme.ecommerce.catalog.mapper;

import com.acme.ecommerce.catalog.dto.ProductAttributeResponse;
import com.acme.ecommerce.catalog.dto.ProductResponse;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductMapper {
    public ProductResponse toResponse(Product product, List<ProductAttributeValue> attributes) {
        return new ProductResponse(
                product.getId(),
                product.getSellerProfile().getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getPrice(),
                product.getCurrency(),
                product.getStatus(),
                product.getVersionNumber(),
                attributes.stream().map(this::toAttributeResponse).toList()
        );
    }

    private ProductAttributeResponse toAttributeResponse(ProductAttributeValue value) {
        return new ProductAttributeResponse(
                value.getAttributeDefinition().getCode(),
                value.getAttributeDefinition().getName(),
                value.getValue()
        );
    }
}
