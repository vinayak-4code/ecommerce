package com.acme.ecommerce.inventory.repository;

import com.acme.ecommerce.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId and i.warehouse.id = :warehouseId")
    Optional<InventoryItem> lockByProductIdAndWarehouseId(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId and i.availableQuantity > 0 order by i.createdAt asc")
    List<InventoryItem> lockAvailableByProductId(@Param("productId") UUID productId);

    @Query("select coalesce(sum(i.availableQuantity), 0) from InventoryItem i where i.productId = :productId")
    long sumAvailableByProductId(@Param("productId") UUID productId);
}
