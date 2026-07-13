package com.acme.ecommerce.seller.entity;

import com.acme.ecommerce.seller.enums.WarehouseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "warehouses", indexes = {
        @Index(name = "idx_warehouse_seller", columnList = "seller_id")
})
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerProfile sellerProfile;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(name = "address_line1", nullable = false, length = 240)
    private String addressLine1;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 120)
    private String state;

    @Column(nullable = false, length = 120)
    private String country;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
