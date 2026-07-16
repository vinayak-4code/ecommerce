package com.acme.ecommerce.search.service;

import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductAttributeValueRepository;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.common.event.DomainEventEnvelope;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.inventory.service.InventoryService;
import com.acme.ecommerce.search.entity.ProductSearchDocument;
import com.acme.ecommerce.search.repository.ProductSearchDocumentRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for event-driven search projection synchronization.
 *
 * <p>The projection mirrors product details, customer-visible attributes, status,
 * and consolidated inventory so public search can avoid joining transactional tables.</p>
 */
@ExtendWith(MockitoExtension.class)
class SearchIndexSyncServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductAttributeValueRepository attributeValueRepository;

    @Mock
    private ProductSearchDocumentRepository searchDocumentRepository;

    @Mock
    private InventoryService inventoryService;

    private SearchIndexSyncService searchIndexSyncService;

    @BeforeEach
    void setUp() {
        searchIndexSyncService = new SearchIndexSyncService(productRepository, attributeValueRepository, searchDocumentRepository, inventoryService, new ObjectMapper());
    }

    @Test
    void syncProduct_shouldSaveSearchDocument_whenProductIsNotDeleted() {
        // Given
        UUID productId = UUID.randomUUID();
        Product product = product(productId, ProductStatus.PUBLISHED);
        ProductAttributeValue visibleAttribute = attributeValue(product, "ram", "16GB", true);
        ProductAttributeValue hiddenAttribute = attributeValue(product, "internal_grade", "A", false);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(searchDocumentRepository.findById(productId)).thenReturn(Optional.empty());
        when(attributeValueRepository.findByProductId(productId)).thenReturn(List.of(visibleAttribute, hiddenAttribute));
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(12L);

        // When
        searchIndexSyncService.syncProduct(productId);

        // Then
        ArgumentCaptor<ProductSearchDocument> documentCaptor = ArgumentCaptor.forClass(ProductSearchDocument.class);
        verify(searchDocumentRepository).save(documentCaptor.capture());
        ProductSearchDocument document = documentCaptor.getValue();
        assertThat(document.getProductId()).isEqualTo(productId);
        assertThat(document.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(document.getTotalAvailableQuantity()).isEqualTo(12L);
        assertThat(document.getAttributesJson()).contains("ram").doesNotContain("internal_grade");
    }

    @Test
    void syncProduct_shouldDeleteSearchDocument_whenProductIsDeleted() {
        // Given
        UUID productId = UUID.randomUUID();
        Product product = product(productId, ProductStatus.DELETED);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        // When
        searchIndexSyncService.syncProduct(productId);

        // Then
        verify(searchDocumentRepository).deleteById(productId);
        verify(searchDocumentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onDomainEvent_shouldSyncProductDirectly_whenEventIsProductEvent() {
        // Given
        UUID productId = UUID.randomUUID();
        Product product = product(productId, ProductStatus.PUBLISHED);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(searchDocumentRepository.findById(productId)).thenReturn(Optional.empty());
        when(attributeValueRepository.findByProductId(productId)).thenReturn(List.of());
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(5L);

        // When
        searchIndexSyncService.onDomainEvent(new DomainEventEnvelope(productId, "Product", DomainEventType.PRODUCT_UPDATED, "{}"));

        // Then
        verify(searchDocumentRepository).save(org.mockito.ArgumentMatchers.any(ProductSearchDocument.class));
    }

    @Test
    void onDomainEvent_shouldExtractProductIdAndSyncProduct_whenEventIsInventoryEvent() {
        // Given
        UUID productId = UUID.randomUUID();
        Product product = product(productId, ProductStatus.PUBLISHED);
        String payloadJson = "{\"productId\":\"" + productId + "\"}";
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(searchDocumentRepository.findById(productId)).thenReturn(Optional.empty());
        when(attributeValueRepository.findByProductId(productId)).thenReturn(List.of());
        when(inventoryService.consolidatedAvailable(productId)).thenReturn(3L);

        // When
        searchIndexSyncService.onDomainEvent(new DomainEventEnvelope(UUID.randomUUID(), "Inventory", DomainEventType.INVENTORY_ADJUSTED, payloadJson));

        // Then
        verify(searchDocumentRepository).save(org.mockito.ArgumentMatchers.any(ProductSearchDocument.class));
    }

    @Test
    void onDomainEvent_shouldIgnoreInventoryEvent_whenPayloadDoesNotContainProductId() {
        // Given
        DomainEventEnvelope event = new DomainEventEnvelope(UUID.randomUUID(), "Inventory", DomainEventType.INVENTORY_ADJUSTED, "{}");

        // When
        searchIndexSyncService.onDomainEvent(event);

        // Then
        verify(productRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(searchDocumentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Product product(UUID productId, ProductStatus status) {
        SellerProfile seller = new SellerProfile();
        seller.setId(UUID.randomUUID());

        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Laptops");

        Product product = new Product();
        product.setId(productId);
        product.setSellerProfile(seller);
        product.setCategory(category);
        product.setName("Gaming Laptop");
        product.setDescription("Fast laptop");
        product.setSku("LAP-1");
        product.setPrice(new BigDecimal("999.99"));
        product.setCurrency(CurrencyCode.INR);
        product.setStatus(status);
        return product;
    }

    private ProductAttributeValue attributeValue(Product product, String code, String value, boolean visible) {
        CategoryAttributeDefinition definition = new CategoryAttributeDefinition();
        definition.setCode(code);
        definition.setName(code);
        definition.setLabelText(code);
        definition.setAttributeType(AttributeType.STRING);
        definition.setVisibleToCustomer(visible);

        ProductAttributeValue attributeValue = new ProductAttributeValue();
        attributeValue.setProduct(product);
        attributeValue.setAttributeDefinition(definition);
        attributeValue.setValue(value);
        return attributeValue;
    }
}
