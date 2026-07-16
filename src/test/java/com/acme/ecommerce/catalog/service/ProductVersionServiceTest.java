package com.acme.ecommerce.catalog.service;

import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import com.acme.ecommerce.catalog.entity.ProductVersion;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.repository.ProductAttributeValueRepository;
import com.acme.ecommerce.catalog.repository.ProductVersionRepository;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for product version snapshots and retention.
 *
 * <p>Product versioning persists compact JSON snapshots for audit and rollback.
 * Tests verify snapshot content, lookup behavior, and pruning of older versions
 * beyond the retention window.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductVersionServiceTest {

    @Mock
    private ProductVersionRepository productVersionRepository;

    @Mock
    private ProductAttributeValueRepository attributeValueRepository;

    private ProductVersionService productVersionService;

    @BeforeEach
    void setUp() {
        productVersionService = new ProductVersionService(productVersionRepository, attributeValueRepository, new ObjectMapper());
    }

    @Test
    void capture_shouldSaveSnapshotWithProductFieldsAndAttributes() {
        // Given
        UUID actorUserId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        ProductAttributeValue attribute = attribute(product, "ram", "16GB", true);

        when(attributeValueRepository.findByProductId(product.getId())).thenReturn(List.of(attribute));
        when(productVersionRepository.save(any(ProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVersionRepository.findVersionIdsForRetention(any(UUID.class), any(Pageable.class))).thenReturn(List.of());
        when(productVersionRepository.findByProductIdOrderByVersionNumberDesc(product.getId())).thenReturn(List.of());

        // When
        productVersionService.capture(product, actorUserId);

        // Then
        ArgumentCaptor<ProductVersion> savedVersion = ArgumentCaptor.forClass(ProductVersion.class);
        verify(productVersionRepository).save(savedVersion.capture());
        assertThat(savedVersion.getValue().getProductId()).isEqualTo(product.getId());
        assertThat(savedVersion.getValue().getVersionNumber()).isEqualTo(product.getVersionNumber());
        assertThat(savedVersion.getValue().getCreatedBy()).isEqualTo(actorUserId);
        assertThat(savedVersion.getValue().getSnapshotJson()).contains("Keyboard", "16GB", "PUBLISHED");
    }

    @Test
    void capture_shouldDeleteVersionsOutsideRetentionWindow() {
        // Given
        UUID retainedId = UUID.randomUUID();
        UUID deleteId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        ProductVersion retained = productVersion(retainedId, product.getId(), 51, "{}");
        ProductVersion old = productVersion(deleteId, product.getId(), 1, "{}");

        when(attributeValueRepository.findByProductId(product.getId())).thenReturn(List.of());
        when(productVersionRepository.save(any(ProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVersionRepository.findVersionIdsForRetention(any(UUID.class), any(Pageable.class))).thenReturn(List.of(retainedId));
        when(productVersionRepository.findByProductIdOrderByVersionNumberDesc(product.getId())).thenReturn(List.of(retained, old));

        // When
        productVersionService.capture(product, UUID.randomUUID());

        // Then
        verify(productVersionRepository).deleteByIds(List.of(deleteId));
    }

    @Test
    void list_shouldReturnVersionResponsesInRepositoryOrder() {
        // Given
        UUID productId = UUID.randomUUID();
        ProductVersion version = productVersion(UUID.randomUUID(), productId, 2, "{\"name\":\"Keyboard\"}");
        when(productVersionRepository.findByProductIdOrderByVersionNumberDesc(productId)).thenReturn(List.of(version));

        // When
        var response = productVersionService.list(productId);

        // Then
        assertThat(response).hasSize(1);
        assertThat(response.get(0).productId()).isEqualTo(productId);
        assertThat(response.get(0).versionNumber()).isEqualTo(2);
    }

    @Test
    void requireSnapshot_shouldDeserializeSnapshot_whenVersionExists() throws Exception {
        // Given
        UUID productId = UUID.randomUUID();
        String json = "{\"categoryId\":\"" + UUID.randomUUID() + "\",\"name\":\"Keyboard\",\"description\":\"Mechanical\",\"sku\":\"SKU-1\",\"price\":49.99,\"currency\":\"USD\",\"status\":\"PUBLISHED\",\"attributes\":{\"ram\":\"16GB\"}}";
        ProductVersion version = productVersion(UUID.randomUUID(), productId, 3, json);
        when(productVersionRepository.findByProductIdAndVersionNumber(productId, 3)).thenReturn(Optional.of(version));

        // When
        ProductVersionService.ProductSnapshot snapshot = productVersionService.requireSnapshot(productId, 3);

        // Then
        assertThat(snapshot.name()).isEqualTo("Keyboard");
        assertThat(snapshot.sku()).isEqualTo("SKU-1");
        assertThat(snapshot.attributes()).containsEntry("ram", "16GB");
    }

    @Test
    void requireSnapshot_shouldThrowResourceNotFoundException_whenVersionIsMissing() {
        // Given
        UUID productId = UUID.randomUUID();
        when(productVersionRepository.findByProductIdAndVersionNumber(productId, 99)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> productVersionService.requireSnapshot(productId, 99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product version not found");
    }

    @Test
    void capture_shouldSkipDelete_whenAllVersionsAreRetained() {
        // Given
        UUID retainedId = UUID.randomUUID();
        Product product = product(UUID.randomUUID());
        ProductVersion retained = productVersion(retainedId, product.getId(), 1, "{}");

        when(attributeValueRepository.findByProductId(product.getId())).thenReturn(List.of());
        when(productVersionRepository.save(any(ProductVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVersionRepository.findVersionIdsForRetention(any(UUID.class), any(Pageable.class))).thenReturn(List.of(retainedId));
        when(productVersionRepository.findByProductIdOrderByVersionNumberDesc(product.getId())).thenReturn(List.of(retained));

        // When
        productVersionService.capture(product, UUID.randomUUID());

        // Then
        verify(productVersionRepository, never()).deleteByIds(any());
    }

    private Product product(UUID productId) {
        SellerProfile seller = new SellerProfile();
        seller.setId(UUID.randomUUID());
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setName("Keyboards");
        Product product = new Product();
        product.setId(productId);
        product.setSellerProfile(seller);
        product.setCategory(category);
        product.setName("Keyboard");
        product.setDescription("Mechanical");
        product.setSku("SKU-1");
        product.setPrice(new BigDecimal("49.99"));
        product.setCurrency(CurrencyCode.USD);
        product.setStatus(ProductStatus.PUBLISHED);
        product.setVersionNumber(2);
        return product;
    }

    private ProductAttributeValue attribute(Product product, String code, String value, boolean visible) {
        CategoryAttributeDefinition definition = new CategoryAttributeDefinition();
        definition.setId(UUID.randomUUID());
        definition.setCode(code);
        definition.setName(code);
        definition.setLabelText(code);
        definition.setAttributeType(AttributeType.STRING);
        definition.setVisibleToCustomer(visible);
        ProductAttributeValue attribute = new ProductAttributeValue();
        attribute.setId(UUID.randomUUID());
        attribute.setProduct(product);
        attribute.setAttributeDefinition(definition);
        attribute.setValue(value);
        return attribute;
    }

    private ProductVersion productVersion(UUID id, UUID productId, int versionNumber, String snapshotJson) {
        ProductVersion version = new ProductVersion();
        version.setId(id);
        version.setProductId(productId);
        version.setVersionNumber(versionNumber);
        version.setSnapshotJson(snapshotJson);
        version.setCreatedBy(UUID.randomUUID());
        return version;
    }
}
