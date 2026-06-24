package com.acme.ecommerce.customer.repository;

import com.acme.ecommerce.customer.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByUserAccountId(UUID userId);
}
