package com.acme.ecommerce.catalog.entity;

import com.acme.ecommerce.catalog.enums.AttributeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "category_attribute_definitions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_category_attr_code", columnNames = {"category_id", "code"})
})
public class CategoryAttributeDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false, length = 40)
    private AttributeType attributeType;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean searchable = true;
}
