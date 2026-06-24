package com.acme.ecommerce.catalog.repository;

import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, UUID> {
    List<ProductAttributeValue> findByProductId(UUID productId);
    void deleteByProductId(UUID productId);
}
