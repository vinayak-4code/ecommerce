package com.acme.ecommerce.catalog.entity;

import com.acme.ecommerce.catalog.enums.AttributeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

    @Column(name = "label_text", nullable = false, length = 160)
    private String labelText;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false, length = 40)
    private AttributeType attributeType;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean searchable = true;

    @Column(name = "visible_to_customer", nullable = false)
    private boolean visibleToCustomer = true;

    @Column(name = "min_length")
    private Integer minLength;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "min_value", precision = 19, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 19, scale = 4)
    private BigDecimal maxValue;

    @Column(name = "allowed_values", length = 1000)
    private String allowedValues;
}
