package com.acme.ecommerce.catalog.repository;

import com.acme.ecommerce.catalog.entity.ProductVersion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVersionRepository extends JpaRepository<ProductVersion, UUID> {
    List<ProductVersion> findByProductIdOrderByVersionNumberDesc(UUID productId);

    Optional<ProductVersion> findByProductIdAndVersionNumber(UUID productId, int versionNumber);

    @Query("select v.id from ProductVersion v where v.productId = :productId order by v.versionNumber desc")
    List<UUID> findVersionIdsForRetention(@Param("productId") UUID productId, Pageable pageable);

    @Modifying
    @Query("delete from ProductVersion v where v.id in :ids")
    void deleteByIds(@Param("ids") List<UUID> ids);
}
