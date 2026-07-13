package com.acme.ecommerce.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "product_attribute_values", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_attribute", columnNames = {"product_id", "attribute_definition_id"})
})
public class ProductAttributeValue {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attribute_definition_id", nullable = false)
    private CategoryAttributeDefinition attributeDefinition;

    @Column(name = "attribute_value", nullable = false, length = 400)
    private String value;
}
