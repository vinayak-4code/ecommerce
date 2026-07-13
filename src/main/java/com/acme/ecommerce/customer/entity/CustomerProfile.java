package com.acme.ecommerce.customer.entity;

import com.acme.ecommerce.auth.entity.UserAccount;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "customer_profiles")
public class CustomerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserAccount userAccount;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "phone_number", length = 40)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
