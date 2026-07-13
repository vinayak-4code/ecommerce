package com.acme.ecommerce.search.entity;

import com.acme.ecommerce.catalog.enums.ProductStatus;
import com.acme.ecommerce.common.money.CurrencyCode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "product_search_documents", indexes = {
        @Index(name = "idx_search_status", columnList = "status"),
        @Index(name = "idx_search_category", columnList = "category_id"),
        @Index(name = "idx_search_name", columnList = "name")
})
public class ProductSearchDocument {
    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "category_name", nullable = false, length = 140)
    private String categoryName;

    @Column(nullable = false, length = 220)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProductStatus status;

    @Column(name = "attributes_json", nullable = false, columnDefinition = "text")
    private String attributesJson;

    @Column(name = "total_available_quantity", nullable = false)
    private long totalAvailableQuantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
