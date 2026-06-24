package com.acme.ecommerce.seller.repository;

import com.acme.ecommerce.seller.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    List<Warehouse> findBySellerProfileId(UUID sellerId);

    Optional<Warehouse> findByIdAndSellerProfileId(UUID id, UUID sellerId);
}
