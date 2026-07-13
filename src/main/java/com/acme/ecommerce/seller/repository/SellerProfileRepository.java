package com.acme.ecommerce.seller.repository;

import com.acme.ecommerce.seller.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, UUID> {
    Optional<SellerProfile> findByUserAccountId(UUID userId);
}
