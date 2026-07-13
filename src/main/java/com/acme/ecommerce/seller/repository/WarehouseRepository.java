package com.acme.ecommerce.seller.repository;

import com.acme.ecommerce.seller.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Page<Warehouse> findBySellerProfileId(UUID sellerId, Pageable pageable);

    Optional<Warehouse> findByIdAndSellerProfileId(UUID id, UUID sellerId);
}
