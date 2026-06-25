package com.acme.ecommerce.catalog.repository;

import com.acme.ecommerce.catalog.entity.CategoryAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryAttributeDefinitionRepository extends JpaRepository<CategoryAttributeDefinition, UUID> {
    List<CategoryAttributeDefinition> findByCategoryId(UUID categoryId);

    Optional<CategoryAttributeDefinition> findByCategoryIdAndCode(UUID categoryId, String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByCategoryId(UUID categoryId);
}
