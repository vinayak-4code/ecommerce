package com.acme.ecommerce.catalog.service;

import com.acme.ecommerce.catalog.dto.*;
import com.acme.ecommerce.catalog.entity.Category;
import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
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
import com.acme.ecommerce.common.money.MoneyUtil;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.service.SellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    private static final String AGGREGATE_TYPE = "Product";

    private final ProductRepository productRepository;
    private final ProductAttributeValueRepository attributeValueRepository;
    private final CategoryAttributeDefinitionRepository attributeDefinitionRepository;
    private final SellerService sellerService;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;
    private final ProductAttributeValidator productAttributeValidator;
    private final ProductVersionService productVersionService;
    private final DomainEventPublisher domainEventPublisher;

    @Transactional
    public ProductResponse create(UUID sellerUserId, CreateProductRequest request) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Category category = categoryService.requireCategory(request.categoryId());
        List<CategoryAttributeDefinition> definitions = attributeDefinitionRepository.findByCategoryId(category.getId());
        productAttributeValidator.validate(definitions, request.attributes());

        Product product = new Product();
        product.setSellerProfile(seller);
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setSku(request.sku().trim().toUpperCase());
        product.setPrice(MoneyUtil.money(request.price()));
        product.setCurrency(request.currency());
        product.setStatus(ProductStatus.DRAFT);
        Product saved = productRepository.save(product);
        List<ProductAttributeValue> attributes = replaceAttributes(saved, definitions, request.attributes());
        productVersionService.capture(saved, sellerUserId);
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, DomainEventType.PRODUCT_CREATED, Map.of("productId", saved.getId()));
        return productMapper.toResponse(saved, attributes);
    }

    @Transactional
    public ProductResponse update(UUID sellerUserId, UUID productId, UpdateProductRequest request) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Product product = requireSellerProduct(productId, seller.getId());
        ensureProductIsMutable(product);
        Category category = categoryService.requireCategory(request.categoryId());
        List<CategoryAttributeDefinition> definitions = attributeDefinitionRepository.findByCategoryId(category.getId());
        productAttributeValidator.validate(definitions, request.attributes());

        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setSku(request.sku().trim().toUpperCase());
        product.setPrice(MoneyUtil.money(request.price()));
        product.setCurrency(request.currency());
        product.setVersionNumber(product.getVersionNumber() + 1);
        Product saved = productRepository.save(product);
        List<ProductAttributeValue> attributes = replaceAttributes(saved, definitions, request.attributes());
        productVersionService.capture(saved, sellerUserId);
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, DomainEventType.PRODUCT_UPDATED, Map.of("productId", saved.getId()));
        return productMapper.toResponse(saved, attributes);
    }

    @Transactional
    public ProductResponse publish(UUID sellerUserId, UUID productId) {
        return changeStatus(sellerUserId, productId, ProductStatus.PUBLISHED, DomainEventType.PRODUCT_PUBLISHED);
    }

    @Transactional
    public ProductResponse unpublish(UUID sellerUserId, UUID productId) {
        return changeStatus(sellerUserId, productId, ProductStatus.UNPUBLISHED, DomainEventType.PRODUCT_UNPUBLISHED);
    }

    @Transactional
    public void delete(UUID sellerUserId, UUID productId) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Product product = requireSellerProduct(productId, seller.getId());
        product.setStatus(ProductStatus.DELETED);
        product.setVersionNumber(product.getVersionNumber() + 1);
        Product saved = productRepository.save(product);
        productVersionService.capture(saved, sellerUserId);
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, DomainEventType.PRODUCT_DELETED, Map.of("productId", saved.getId()));
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID productId) {
        Product product = requireProduct(productId);
        return productMapper.toResponse(product, attributeValueRepository.findByProductId(productId));
    }

    @Transactional(readOnly = true)
    public Product requireProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    @Transactional(readOnly = true)
    public Product requirePublishedProduct(UUID productId) {
        Product product = requireProduct(productId);
        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Product is not available for purchase");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public List<ProductVersionResponse> versions(UUID productId) {
        requireProduct(productId);
        return productVersionService.list(productId);
    }

    @Transactional
    public ProductResponse rollback(UUID sellerUserId, UUID productId, int versionNumber) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Product product = requireSellerProduct(productId, seller.getId());
        ProductVersionService.ProductSnapshot snapshot = productVersionService.requireSnapshot(productId, versionNumber);
        Category category = categoryService.requireCategory(snapshot.categoryId());
        List<AttributeValueRequest> attributes = snapshot.attributes().entrySet().stream()
                .map(entry -> new AttributeValueRequest(entry.getKey(), entry.getValue()))
                .toList();
        List<CategoryAttributeDefinition> definitions = attributeDefinitionRepository.findByCategoryId(category.getId());
        productAttributeValidator.validate(definitions, attributes);

        product.setCategory(category);
        product.setName(snapshot.name());
        product.setDescription(snapshot.description());
        product.setSku(snapshot.sku());
        product.setPrice(MoneyUtil.money(snapshot.price()));
        product.setCurrency(CurrencyCode.valueOf(snapshot.currency()));
        product.setStatus(ProductStatus.valueOf(snapshot.status()));
        product.setVersionNumber(product.getVersionNumber() + 1);
        Product saved = productRepository.save(product);
        List<ProductAttributeValue> savedAttributes = replaceAttributes(saved, definitions, attributes);
        productVersionService.capture(saved, sellerUserId);
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, DomainEventType.PRODUCT_UPDATED, Map.of("productId", saved.getId(), "rollbackFrom", versionNumber));
        return productMapper.toResponse(saved, savedAttributes);
    }

    private ProductResponse changeStatus(UUID sellerUserId, UUID productId, ProductStatus status, DomainEventType eventType) {
        SellerProfile seller = sellerService.requireByUserId(sellerUserId);
        Product product = requireSellerProduct(productId, seller.getId());
        ensureProductIsMutable(product);
        product.setStatus(status);
        product.setVersionNumber(product.getVersionNumber() + 1);
        Product saved = productRepository.save(product);
        List<ProductAttributeValue> attributes = attributeValueRepository.findByProductId(saved.getId());
        productVersionService.capture(saved, sellerUserId);
        domainEventPublisher.publish(saved.getId(), AGGREGATE_TYPE, eventType, Map.of("productId", saved.getId(), "status", status));
        return productMapper.toResponse(saved, attributes);
    }

    private Product requireSellerProduct(UUID productId, UUID sellerId) {
        return productRepository.findByIdAndSellerProfileId(productId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for seller"));
    }

    private void ensureProductIsMutable(Product product) {
        if (product.getStatus() == ProductStatus.DELETED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "Deleted product cannot be modified");
        }
    }

    private List<ProductAttributeValue> replaceAttributes(Product product, List<CategoryAttributeDefinition> definitions, List<AttributeValueRequest> attributes) {
        attributeValueRepository.deleteByProductId(product.getId());
        List<AttributeValueRequest> safeAttributes = attributes == null ? List.of() : attributes;
        List<ProductAttributeValue> values = safeAttributes.stream()
                .map(request -> toValue(product, definitions, request))
                .toList();
        return attributeValueRepository.saveAll(values);
    }

    private ProductAttributeValue toValue(Product product, List<CategoryAttributeDefinition> definitions, AttributeValueRequest request) {
        CategoryAttributeDefinition definition = definitions.stream()
                .filter(item -> item.getCode().equals(request.code()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "Attribute not allowed: " + request.code()));
        ProductAttributeValue value = new ProductAttributeValue();
        value.setProduct(product);
        value.setAttributeDefinition(definition);
        value.setValue(request.value().trim());
        return value;
    }
}
