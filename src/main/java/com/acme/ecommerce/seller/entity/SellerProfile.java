package com.acme.ecommerce.seller.entity;

import com.acme.ecommerce.auth.entity.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "seller_profiles")
public class SellerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount userAccount;

    @Column(name = "business_name", nullable = false, length = 180)
    private String businessName;

    @Column(name = "contact_number", length = 40)
    private String contactNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
