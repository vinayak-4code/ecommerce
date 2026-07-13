package com.acme.ecommerce.catalog.repository;

import com.acme.ecommerce.catalog.entity.ProductAttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, UUID> {
    List<ProductAttributeValue> findByProductId(UUID productId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProductAttributeValue v WHERE v.product.id = :productId")
    void deleteByProductId(UUID productId);
}
