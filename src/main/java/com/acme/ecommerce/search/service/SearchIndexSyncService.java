package com.acme.ecommerce.search.service;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductAttributeValueRepository;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.event.DomainEventEnvelope;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.inventory.service.InventoryService;
import com.acme.ecommerce.search.entity.ProductSearchDocument;
import com.acme.ecommerce.search.repository.ProductSearchDocumentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Event-driven search projection updater.
 *
 * <p>It consumes in-process domain events in the demo. In a production split,
 * this class becomes a Kafka consumer that updates OpenSearch documents with
 * product details, attributes, status, and consolidated inventory.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexSyncService {
    private final ProductRepository productRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final ProductSearchDocumentRepository searchDocumentRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    /**
     * Handles product/inventory events and refreshes the affected product document.
     */
    @EventListener
    @Transactional
    public void onDomainEvent(DomainEventEnvelope event) {
        if (isProductEvent(event.eventType())) {
            syncProduct(event.aggregateId());
        } else if (isInventoryEvent(event.eventType())) {
            extractProductId(event.payloadJson()).ifPresent(this::syncProduct);
        }
    }

    /**
     * Rebuilds one product search document from source-of-truth tables.
     */
    public void syncProduct(UUID productId) {
        productRepository.findById(productId).ifPresent(product -> {
            if (product.getStatus() == ProductStatus.DELETED) {
                searchDocumentRepository.deleteById(product.getId());
                return;
            }
            ProductSearchDocument document = searchDocumentRepository.findById(product.getId()).orElseGet(ProductSearchDocument::new);
            document.setProductId(product.getId());
            document.setSellerId(product.getSellerProfile().getId());
            document.setCategoryId(product.getCategory().getId());
            document.setCategoryName(product.getCategory().getName());
            document.setName(product.getName());
            document.setDescription(product.getDescription());
            document.setSku(product.getSku());
            document.setPrice(product.getPrice());
            document.setCurrency(product.getCurrency());
            document.setStatus(product.getStatus());
            document.setAttributesJson(attributesJson(product));
            document.setTotalAvailableQuantity(inventoryService.consolidatedAvailable(product.getId()));
            document.setUpdatedAt(Instant.now());
            searchDocumentRepository.save(document);
        });
    }

    private boolean isProductEvent(DomainEventType eventType) {
        return switch (eventType) {
            case PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED, PRODUCT_PUBLISHED, PRODUCT_UNPUBLISHED -> true;
            default -> false;
        };
    }

    private boolean isInventoryEvent(DomainEventType eventType) {
        return switch (eventType) {
            case INVENTORY_ADDED, INVENTORY_ADJUSTED, INVENTORY_RESERVED, INVENTORY_RELEASED, INVENTORY_CONSUMED -> true;
            default -> false;
        };
    }

    private java.util.Optional<UUID> extractProductId(String payloadJson) {
        try {
            Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {
            });
            Object productId = payload.get("productId");
            return productId == null ? java.util.Optional.empty() : java.util.Optional.of(UUID.fromString(String.valueOf(productId)));
        } catch (Exception exception) {
            log.warn("Failed to extract productId from event payload", exception);
            return java.util.Optional.empty();
        }
    }

    private String attributesJson(Product product) {
        Map<String, String> attributes = attributeValueRepository.findByProductId(product.getId()).stream()
                .filter(value -> value.getAttributeDefinition().isVisibleToCustomer())
                .collect(Collectors.toMap(value -> value.getAttributeDefinition().getCode(), ProductAttributeValue::getValue));
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize product attributes for search index", exception);
        }
    }
}
