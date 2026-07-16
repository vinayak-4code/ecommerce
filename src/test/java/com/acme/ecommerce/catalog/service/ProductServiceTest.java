package com.acme.ecommerce.catalog.service;

import com.acme.ecommerce.catalog.dto.AttributeValueRequest;
import com.acme.ecommerce.catalog.dto.CreateProductRequest;
import com.acme.ecommerce.catalog.dto.ProductResponse;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import com.acme.ecommerce.catalog.enums.AttributeType;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.mapper.ProductMapper;
import com.acme.ecommerce.catalog.repository.CategoryAttributeDefinitionRepository;
import com.acme.ecommerce.catalog.repository.ProductAttributeValueRepository;
import com.acme.ecommerce.catalog.repository.ProductRepository;
import com.acme.ecommerce.catalog.validation.ProductAttributeValidator;
import com.acme.ecommerce.common.event.DomainEventPublisher;
import com.acme.ecommerce.common.event.DomainEventType;
import com.acme.ecommerce.common.exception.BusinessException;
import com.acme.ecommerce.common.exception.ErrorCode;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.service.SellerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit specification for seller product service behavior that supports product listing.
 *
 * <p>DTOs and mapper conversion are intentionally mocked. The tests verify that
 * product commands enforce ownership/category rules and that listing calls are
 * bounded and delegated to the correct repository queries.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductAttributeValueRepository attributeValueRepository;

    @Mock
    private CategoryAttributeDefinitionRepository attributeDefinitionRepository;

    @Mock
    private SellerService sellerService;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductAttributeValidator productAttributeValidator;

    @Mock
    private ProductVersionService productVersionService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private ProductService productService;

    @Test
    void create_shouldSaveDraftProductReplaceAttributesCaptureVersionAndPublishEvent() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Category category = category(categoryId);
        CategoryAttributeDefinition colorDefinition = definition(category, "color", AttributeType.SELECT);
        CreateProductRequest request = new CreateProductRequest(
                categoryId,
                "  Gaming Keyboard  ",
                "Mechanical keyboard",
                " kb-100 ",
                new BigDecimal("49.999"),
                CurrencyCode.INR,
                List.of(new AttributeValueRequest(" Color ", "Black"))
        );
        ProductResponse expectedResponse = productResponse(productId, sellerId, categoryId, "Gaming Keyboard", ProductStatus.DRAFT);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(categoryService.requireCategory(categoryId)).thenReturn(category);
        when(attributeDefinitionRepository.findByCategoryId(categoryId)).thenReturn(List.of(colorDefinition));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(productId);
            return saved;
        });
        when(attributeValueRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productMapper.toResponse(any(Product.class), anyList())).thenReturn(expectedResponse);

        // When
        ProductResponse response = productService.create(sellerUserId, request);

        // Then
        assertThat(response).isSameAs(expectedResponse);

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(product.capture());
        assertThat(product.getValue().getSellerProfile()).isSameAs(seller);
        assertThat(product.getValue().getCategory()).isSameAs(category);
        assertThat(product.getValue().getName()).isEqualTo("Gaming Keyboard");
        assertThat(product.getValue().getSku()).isEqualTo("KB-100");
        assertThat(product.getValue().getPrice()).isEqualByComparingTo("50.00");
        assertThat(product.getValue().getStatus()).isEqualTo(ProductStatus.DRAFT);

        verify(categoryService).ensureLeafCategory(category);
        verify(productAttributeValidator).validate(List.of(colorDefinition), request.attributes());
        verify(attributeValueRepository).deleteByProductId(productId);
        verify(productVersionService).capture(product.getValue(), sellerUserId);
        verify(domainEventPublisher).publish(eq(productId), eq("Product"), eq(DomainEventType.PRODUCT_CREATED), anyMap());
    }

    @Test
    void create_shouldNotSaveProduct_whenAttributeValidationFails() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Category category = category(categoryId);
        List<CategoryAttributeDefinition> definitions = List.of(definition(category, "color", AttributeType.SELECT));
        CreateProductRequest request = createProductRequest(categoryId);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(categoryService.requireCategory(categoryId)).thenReturn(category);
        when(attributeDefinitionRepository.findByCategoryId(categoryId)).thenReturn(definitions);
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.VALIDATION_FAILED, "Invalid attribute"))
                .when(productAttributeValidator).validate(definitions, request.attributes());

        // When / Then
        assertThatThrownBy(() -> productService.create(sellerUserId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid attribute");

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(productVersionService, domainEventPublisher, productMapper);
    }

    @Test
    void listSellerProducts_shouldResolveSellerClampPageSizeAndMapProducts() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product product = product(productId, seller, category(categoryId), ProductStatus.PUBLISHED);
        ProductResponse expectedResponse = productResponse(productId, sellerId, categoryId, "Keyboard", ProductStatus.PUBLISHED);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findBySellerProfileId(eq(sellerId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(product)));
        when(attributeValueRepository.findByProductId(productId)).thenReturn(List.of());
        when(productMapper.toResponse(product, List.of())).thenReturn(expectedResponse);

        // When
        Page<ProductResponse> page = productService.listSellerProducts(sellerUserId, -5, 500);

        // Then
        assertThat(page.getContent()).containsExactly(expectedResponse);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findBySellerProfileId(eq(sellerId), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void get_shouldReturnProductResponse_whenProductIsPublished() {
        // Given
        UUID sellerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = product(productId, seller(sellerId), category(categoryId), ProductStatus.PUBLISHED);
        ProductResponse expectedResponse = productResponse(productId, sellerId, categoryId, "Keyboard", ProductStatus.PUBLISHED);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(attributeValueRepository.findByProductId(productId)).thenReturn(List.of());
        when(productMapper.toResponse(product, List.of())).thenReturn(expectedResponse);

        // When
        ProductResponse response = productService.get(productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(productMapper).toResponse(product, List.of());
    }

    @Test
    void get_shouldThrowBusinessException_whenProductIsNotPublished() {
        // Given
        UUID productId = UUID.randomUUID();
        Product draft = product(productId, seller(UUID.randomUUID()), category(UUID.randomUUID()), ProductStatus.DRAFT);
        when(productRepository.findById(productId)).thenReturn(Optional.of(draft));

        // When / Then
        assertThatThrownBy(() -> productService.get(productId))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
                    assertThat(exception).hasMessageContaining("Product is not available for purchase");
                });

        verifyNoInteractions(productMapper, attributeValueRepository);
    }

    @Test
    void requireProduct_shouldThrowResourceNotFoundException_whenProductIsMissing() {
        // Given
        UUID productId = UUID.randomUUID();
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> productService.requireProduct(productId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void publish_shouldChangeStatusIncrementVersionCaptureSnapshotPublishEventAndReturnMappedResponse() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product product = product(productId, seller, category(categoryId), ProductStatus.DRAFT);
        product.setVersionNumber(2);
        ProductResponse expectedResponse = productResponse(productId, sellerId, categoryId, "Keyboard", ProductStatus.PUBLISHED);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(attributeValueRepository.findByProductId(productId)).thenReturn(List.of());
        when(productMapper.toResponse(product, List.of())).thenReturn(expectedResponse);

        // When
        ProductResponse response = productService.publish(sellerUserId, productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.PUBLISHED);
        assertThat(product.getVersionNumber()).isEqualTo(3);

        verify(productVersionService).capture(product, sellerUserId);
        verify(domainEventPublisher).publish(eq(productId), eq("Product"), eq(DomainEventType.PRODUCT_PUBLISHED), anyMap());
    }

    @Test
    void publish_shouldThrowBusinessException_whenProductIsDeleted() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product deleted = product(productId, seller, category(UUID.randomUUID()), ProductStatus.DELETED);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(deleted));

        // When / Then
        assertThatThrownBy(() -> productService.publish(sellerUserId, productId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Deleted product cannot be modified");

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(productVersionService, domainEventPublisher);
    }

    @Test
    void delete_shouldSoftDeleteProductIncrementVersionCaptureSnapshotAndPublishEvent() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product product = product(productId, seller, category(UUID.randomUUID()), ProductStatus.PUBLISHED);
        product.setVersionNumber(5);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        // When
        productService.delete(sellerUserId, productId);

        // Then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
        assertThat(product.getVersionNumber()).isEqualTo(6);
        verify(productVersionService).capture(product, sellerUserId);
        verify(domainEventPublisher).publish(eq(productId), eq("Product"), eq(DomainEventType.PRODUCT_DELETED), anyMap());
    }

    @Test
    void rollback_shouldRestoreProductFromSnapshotReplaceAttributesCaptureVersionAndPublishEvent() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Category category = category(categoryId);
        Product product = product(productId, seller, category(UUID.randomUUID()), ProductStatus.PUBLISHED);
        product.setVersionNumber(4);
        ProductVersionService.ProductSnapshot snapshot = new ProductVersionService.ProductSnapshot(
                categoryId,
                "Snapshot Name",
                "Snapshot description",
                "SNAP-1",
                new BigDecimal("99.99"),
                CurrencyCode.INR.name(),
                ProductStatus.DRAFT.name(),
                Map.of("color", "Black")
        );
        CategoryAttributeDefinition colorDefinition = definition(category, "color", AttributeType.STRING);
        ProductResponse expectedResponse = productResponse(productId, sellerId, categoryId, "Snapshot Name", ProductStatus.DRAFT);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(product));
        when(productVersionService.requireSnapshot(productId, 2)).thenReturn(snapshot);
        when(categoryService.requireCategory(categoryId)).thenReturn(category);
        when(attributeDefinitionRepository.findByCategoryId(categoryId)).thenReturn(List.of(colorDefinition));
        when(productRepository.save(product)).thenReturn(product);
        when(attributeValueRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productMapper.toResponse(any(Product.class), anyList())).thenReturn(expectedResponse);

        // When
        ProductResponse response = productService.rollback(sellerUserId, productId, 2);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        assertThat(product.getCategory()).isSameAs(category);
        assertThat(product.getName()).isEqualTo("Snapshot Name");
        assertThat(product.getSku()).isEqualTo("SNAP-1");
        assertThat(product.getPrice()).isEqualByComparingTo("99.99");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.getVersionNumber()).isEqualTo(5);

        verify(productAttributeValidator).validate(eq(List.of(colorDefinition)), anyList());
        verify(attributeValueRepository).deleteByProductId(productId);
        verify(productVersionService).capture(product, sellerUserId);
        verify(domainEventPublisher).publish(eq(productId), eq("Product"), eq(DomainEventType.PRODUCT_UPDATED), anyMap());
    }


    @Test
    void update_shouldChangeMutableProductReplaceAttributesCaptureVersionAndPublishEvent() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Category category = category(categoryId);
        Product product = product(productId, seller, category(UUID.randomUUID()), ProductStatus.DRAFT);
        product.setVersionNumber(7);
        CategoryAttributeDefinition colorDefinition = definition(category, "color", AttributeType.STRING);
        com.acme.ecommerce.catalog.dto.UpdateProductRequest request = new com.acme.ecommerce.catalog.dto.UpdateProductRequest(
                categoryId,
                "  Updated Keyboard  ",
                "Updated description",
                " update-1 ",
                new BigDecimal("79.999"),
                CurrencyCode.INR,
                List.of(new AttributeValueRequest("color", "White"))
        );
        ProductResponse expectedResponse = productResponse(productId, sellerId, categoryId, "Updated Keyboard", ProductStatus.DRAFT);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(product));
        when(categoryService.requireCategory(categoryId)).thenReturn(category);
        when(attributeDefinitionRepository.findByCategoryId(categoryId)).thenReturn(List.of(colorDefinition));
        when(productRepository.save(product)).thenReturn(product);
        when(attributeValueRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(productMapper.toResponse(any(Product.class), anyList())).thenReturn(expectedResponse);

        // When
        ProductResponse response = productService.update(sellerUserId, productId, request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        assertThat(product.getCategory()).isSameAs(category);
        assertThat(product.getName()).isEqualTo("Updated Keyboard");
        assertThat(product.getSku()).isEqualTo("UPDATE-1");
        assertThat(product.getPrice()).isEqualByComparingTo("80.00");
        assertThat(product.getVersionNumber()).isEqualTo(8);

        verify(categoryService).ensureLeafCategory(category);
        verify(productAttributeValidator).validate(List.of(colorDefinition), request.attributes());
        verify(attributeValueRepository).deleteByProductId(productId);
        verify(productVersionService).capture(product, sellerUserId);
        verify(domainEventPublisher).publish(eq(productId), eq("Product"), eq(DomainEventType.PRODUCT_UPDATED), anyMap());
    }

    @Test
    void update_shouldThrowBusinessException_whenProductIsDeleted() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        SellerProfile seller = seller(sellerId);
        Product deleted = product(productId, seller, category(categoryId), ProductStatus.DELETED);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(deleted));

        // When / Then
        assertThatThrownBy(() -> productService.update(sellerUserId, productId, new com.acme.ecommerce.catalog.dto.UpdateProductRequest(
                categoryId,
                "Keyboard",
                "Description",
                "SKU-1",
                new BigDecimal("49.99"),
                CurrencyCode.INR,
                List.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Deleted product cannot be modified");

        verify(productRepository, never()).save(any(Product.class));
        verifyNoInteractions(productVersionService, domainEventPublisher);
    }

    @Test
    void unpublish_shouldChangeStatusIncrementVersionCaptureSnapshotAndPublishEvent() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        SellerProfile seller = seller(sellerId);
        Product product = product(productId, seller, category(categoryId), ProductStatus.PUBLISHED);
        product.setVersionNumber(2);
        ProductResponse expectedResponse = productResponse(productId, sellerId, categoryId, "Keyboard", ProductStatus.UNPUBLISHED);

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);
        when(attributeValueRepository.findByProductId(productId)).thenReturn(List.of());
        when(productMapper.toResponse(product, List.of())).thenReturn(expectedResponse);

        // When
        ProductResponse response = productService.unpublish(sellerUserId, productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.UNPUBLISHED);
        assertThat(product.getVersionNumber()).isEqualTo(3);
        verify(productVersionService).capture(product, sellerUserId);
        verify(domainEventPublisher).publish(eq(productId), eq("Product"), eq(DomainEventType.PRODUCT_UNPUBLISHED), anyMap());
    }

    @Test
    void versions_shouldVerifySellerOwnershipThenReturnProductVersions() {
        // Given
        UUID sellerUserId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SellerProfile seller = seller(sellerId);
        Product product = product(productId, seller, category(UUID.randomUUID()), ProductStatus.PUBLISHED);
        com.acme.ecommerce.catalog.dto.ProductVersionResponse version = new com.acme.ecommerce.catalog.dto.ProductVersionResponse(
                UUID.randomUUID(),
                productId,
                1,
                "{}",
                java.time.Instant.now(),
                sellerUserId
        );

        when(sellerService.requireByUserId(sellerUserId)).thenReturn(seller);
        when(productRepository.findByIdAndSellerProfileId(productId, sellerId)).thenReturn(Optional.of(product));
        when(productVersionService.list(productId)).thenReturn(List.of(version));

        // When
        List<com.acme.ecommerce.catalog.dto.ProductVersionResponse> versions = productService.versions(sellerUserId, productId);

        // Then
        assertThat(versions).containsExactly(version);
        verify(productRepository).findByIdAndSellerProfileId(productId, sellerId);
        verify(productVersionService).list(productId);
    }

    private CreateProductRequest createProductRequest(UUID categoryId) {
        return new CreateProductRequest(
                categoryId,
                "Keyboard",
                "Mechanical keyboard",
                "KB-100",
                new BigDecimal("49.99"),
                CurrencyCode.INR,
                List.of(new AttributeValueRequest("color", "Black"))
        );
    }

    private SellerProfile seller(UUID sellerId) {
        SellerProfile seller = new SellerProfile();
        seller.setId(sellerId);
        seller.setBusinessName("Acme Seller");
        return seller;
    }

    private Category category(UUID categoryId) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("Keyboards");
        category.setSlug("keyboards");
        category.setActive(true);
        return category;
    }

    private CategoryAttributeDefinition definition(Category category, String code, AttributeType type) {
        CategoryAttributeDefinition definition = new CategoryAttributeDefinition();
        definition.setId(UUID.randomUUID());
        definition.setCategory(category);
        definition.setCode(code);
        definition.setName(code);
        definition.setLabelText(code);
        definition.setAttributeType(type);
        definition.setRequired(false);
        definition.setSearchable(true);
        definition.setVisibleToCustomer(true);
        definition.setAllowedValues("Black,White");
        return definition;
    }

    private Product product(UUID productId, SellerProfile seller, Category category, ProductStatus status) {
        Product product = new Product();
        product.setId(productId);
        product.setSellerProfile(seller);
        product.setCategory(category);
        product.setName("Keyboard");
        product.setDescription("Mechanical keyboard");
        product.setSku("KB-100");
        product.setPrice(new BigDecimal("49.99"));
        product.setCurrency(CurrencyCode.INR);
        product.setStatus(status);
        product.setVersionNumber(1);
        return product;
    }

    private ProductResponse productResponse(UUID productId, UUID sellerId, UUID categoryId, String name, ProductStatus status) {
        return new ProductResponse(
                productId,
                sellerId,
                categoryId,
                "Keyboards",
                name,
                "Mechanical keyboard",
                "KB-100",
                new BigDecimal("49.99"),
                CurrencyCode.INR,
                status,
                1,
                List.of()
        );
    }
}
