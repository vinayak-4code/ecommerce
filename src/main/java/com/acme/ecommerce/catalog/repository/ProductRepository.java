package com.acme.ecommerce.catalog.repository;

import com.acme.ecommerce.catalog.entity.Product;
import com.acme.ecommerce.catalog.enums.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndSellerProfileId(UUID id, UUID sellerId);

    List<Product> findByIdIn(Collection<UUID> ids);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findBySellerProfileId(UUID sellerId, Pageable pageable);

    List<Product> findAllBySellerProfileId(UUID sellerId);
}
