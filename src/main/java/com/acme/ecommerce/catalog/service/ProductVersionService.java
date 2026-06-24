package com.acme.ecommerce.catalog.service;

import com.acme.ecommerce.catalog.dto.ProductVersionResponse;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import com.acme.ecommerce.catalog.entity.ProductVersion;
import com.acme.ecommerce.catalog.repository.ProductAttributeValueRepository;
import com.acme.ecommerce.catalog.repository.ProductVersionRepository;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductVersionService {
    private static final int MAX_VERSIONS = 50;

    private final ProductVersionRepository productVersionRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void capture(Product product, UUID actorUserId) {
        List<ProductAttributeValue> attributes = attributeValueRepository.findByProductId(product.getId());
        ProductSnapshot snapshot = ProductSnapshot.from(product, attributes);
        ProductVersion version = new ProductVersion();
        version.setProductId(product.getId());
        version.setVersionNumber(product.getVersionNumber());
        version.setSnapshotJson(toJson(snapshot));
        version.setCreatedBy(actorUserId);
        productVersionRepository.save(version);
        enforceRetention(product.getId());
    }

    @Transactional(readOnly = true)
    public List<ProductVersionResponse> list(UUID productId) {
        return productVersionRepository.findByProductIdOrderByVersionNumberDesc(productId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductSnapshot requireSnapshot(UUID productId, int versionNumber) {
        ProductVersion version = productVersionRepository.findByProductIdAndVersionNumber(productId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Product version not found"));
        try {
            return objectMapper.readValue(version.getSnapshotJson(), ProductSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid product version snapshot", exception);
        }
    }

    private ProductVersionResponse toResponse(ProductVersion version) {
        return new ProductVersionResponse(
                version.getId(),
                version.getProductId(),
                version.getVersionNumber(),
                version.getSnapshotJson(),
                version.getCreatedAt(),
                version.getCreatedBy()
        );
    }

    private void enforceRetention(UUID productId) {
        List<UUID> retained = productVersionRepository.findVersionIdsForRetention(productId, PageRequest.of(0, MAX_VERSIONS));
        List<UUID> all = productVersionRepository.findByProductIdOrderByVersionNumberDesc(productId).stream()
                .map(ProductVersion::getId)
                .toList();
        List<UUID> deleteIds = all.stream().filter(id -> !retained.contains(id)).toList();
        if (!deleteIds.isEmpty()) {
            productVersionRepository.deleteByIds(deleteIds);
        }
    }

    private String toJson(ProductSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to create product version snapshot", exception);
        }
    }

    public record ProductSnapshot(
            UUID categoryId,
            String name,
            String description,
            String sku,
            BigDecimal price,
            String currency,
            String status,
            Map<String, String> attributes
    ) {
        public static ProductSnapshot from(Product product, List<ProductAttributeValue> attributes) {
            Map<String, String> attributeMap = attributes.stream()
                    .collect(Collectors.toMap(value -> value.getAttributeDefinition().getCode(), ProductAttributeValue::getValue));
            return new ProductSnapshot(
                    product.getCategory().getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getSku(),
                    product.getPrice(),
                    product.getCurrency().name(),
                    product.getStatus().name(),
                    attributeMap
            );
        }
    }
}
