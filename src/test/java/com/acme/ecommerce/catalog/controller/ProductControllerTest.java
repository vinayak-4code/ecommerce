package com.acme.ecommerce.catalog.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.catalog.dto.AttributeValueRequest;
import com.acme.ecommerce.catalog.dto.CreateProductRequest;
import com.acme.ecommerce.catalog.dto.ProductResponse;
import com.acme.ecommerce.catalog.dto.ProductVersionResponse;
import com.acme.ecommerce.catalog.dto.UpdateProductRequest;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.catalog.service.ProductService;
import com.acme.ecommerce.common.money.CurrencyCode;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for seller product controller operations.
 *
 * <p>The controller reads the current seller user id from the security context
 * and delegates product listing, creation, publishing, and history operations
 * to ProductService.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private UUID sellerUserId;

    @BeforeEach
    void setUp() {
        sellerUserId = UUID.randomUUID();
        authenticate(sellerUserId, UserRole.SELLER);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldDelegateAuthenticatedSellerAndPaginationToProductService() {
        // Given
        ProductResponse product = productResponse(UUID.randomUUID(), ProductStatus.PUBLISHED);
        Page<ProductResponse> expectedPage = new PageImpl<>(List.of(product));
        when(productService.listSellerProducts(sellerUserId, 0, 20)).thenReturn(expectedPage);

        // When
        Page<ProductResponse> response = productController.list(0, 20);

        // Then
        assertThat(response).isSameAs(expectedPage);
        verify(productService).listSellerProducts(sellerUserId, 0, 20);
    }

    @Test
    void create_shouldDelegateAuthenticatedSellerAndRequestToProductService() {
        // Given
        CreateProductRequest request = createProductRequest();
        ProductResponse expectedResponse = productResponse(UUID.randomUUID(), ProductStatus.DRAFT);
        when(productService.create(sellerUserId, request)).thenReturn(expectedResponse);

        // When
        ProductResponse response = productController.create(request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(productService).create(sellerUserId, request);
    }

    @Test
    void update_shouldDelegateAuthenticatedSellerProductIdAndRequestToProductService() {
        // Given
        UUID productId = UUID.randomUUID();
        UpdateProductRequest request = updateProductRequest();
        ProductResponse expectedResponse = productResponse(productId, ProductStatus.DRAFT);
        when(productService.update(sellerUserId, productId, request)).thenReturn(expectedResponse);

        // When
        ProductResponse response = productController.update(productId, request);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(productService).update(sellerUserId, productId, request);
    }

    @Test
    void get_shouldDelegateProductIdToProductServiceWithoutSellerContext() {
        // Given
        UUID productId = UUID.randomUUID();
        ProductResponse expectedResponse = productResponse(productId, ProductStatus.PUBLISHED);
        when(productService.get(productId)).thenReturn(expectedResponse);

        // When
        ProductResponse response = productController.get(productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(productService).get(productId);
    }

    @Test
    void publish_shouldDelegateAuthenticatedSellerAndProductIdToProductService() {
        // Given
        UUID productId = UUID.randomUUID();
        ProductResponse expectedResponse = productResponse(productId, ProductStatus.PUBLISHED);
        when(productService.publish(sellerUserId, productId)).thenReturn(expectedResponse);

        // When
        ProductResponse response = productController.publish(productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(productService).publish(sellerUserId, productId);
    }

    @Test
    void unpublish_shouldDelegateAuthenticatedSellerAndProductIdToProductService() {
        // Given
        UUID productId = UUID.randomUUID();
        ProductResponse expectedResponse = productResponse(productId, ProductStatus.UNPUBLISHED);
        when(productService.unpublish(sellerUserId, productId)).thenReturn(expectedResponse);

        // When
        ProductResponse response = productController.unpublish(productId);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(productService).unpublish(sellerUserId, productId);
    }

    @Test
    void delete_shouldDelegateAuthenticatedSellerAndProductIdToProductService() {
        // Given
        UUID productId = UUID.randomUUID();

        // When
        productController.delete(productId);

        // Then
        verify(productService).delete(sellerUserId, productId);
    }

    @Test
    void versions_shouldDelegateAuthenticatedSellerAndProductIdToProductService() {
        // Given
        UUID productId = UUID.randomUUID();
        ProductVersionResponse version = new ProductVersionResponse(
                UUID.randomUUID(),
                productId,
                3,
                "{}",
                Instant.now(),
                sellerUserId
        );
        when(productService.versions(sellerUserId, productId)).thenReturn(List.of(version));

        // When
        List<ProductVersionResponse> response = productController.versions(productId);

        // Then
        assertThat(response).containsExactly(version);
        verify(productService).versions(sellerUserId, productId);
    }

    @Test
    void rollback_shouldDelegateAuthenticatedSellerProductIdAndVersionToProductService() {
        // Given
        UUID productId = UUID.randomUUID();
        ProductResponse expectedResponse = productResponse(productId, ProductStatus.DRAFT);
        when(productService.rollback(sellerUserId, productId, 2)).thenReturn(expectedResponse);

        // When
        ProductResponse response = productController.rollback(productId, 2);

        // Then
        assertThat(response).isSameAs(expectedResponse);
        verify(productService).rollback(sellerUserId, productId, 2);
    }

    private void authenticate(UUID userId, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "seller@example.com", role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private CreateProductRequest createProductRequest() {
        return new CreateProductRequest(
                UUID.randomUUID(),
                "Keyboard",
                "Mechanical keyboard",
                "KB-100",
                new BigDecimal("49.99"),
                CurrencyCode.INR,
                List.of(new AttributeValueRequest("color", "Black"))
        );
    }

    private UpdateProductRequest updateProductRequest() {
        return new UpdateProductRequest(
                UUID.randomUUID(),
                "Keyboard V2",
                "Updated mechanical keyboard",
                "KB-200",
                new BigDecimal("59.99"),
                CurrencyCode.INR,
                List.of(new AttributeValueRequest("color", "White"))
        );
    }

    private ProductResponse productResponse(UUID productId, ProductStatus status) {
        return new ProductResponse(
                productId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Keyboards",
                "Keyboard",
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
