package com.acme.ecommerce.inventory.repository;

import com.acme.ecommerce.inventory.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByProductIdAndWarehouseId(UUID productId, UUID warehouseId);

    @Query("select i from InventoryItem i where i.warehouse.sellerProfile.id = :sellerId")
    List<InventoryItem> findBySellerId(@Param("sellerId") UUID sellerId);

    @Query("select count(i) > 0 from InventoryItem i where i.warehouse.id = :warehouseId")
    boolean existsByWarehouseId(@Param("warehouseId") UUID warehouseId);

    @Query("select i from InventoryItem i where i.productId = :productId and i.availableQuantity > 0 order by i.createdAt asc")
    List<InventoryItem> findAvailableByProductId(@Param("productId") UUID productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InventoryItem i set i.availableQuantity = i.availableQuantity - :quantity, i.reservedQuantity = i.reservedQuantity + :quantity, i.updatedAt = current_timestamp where i.productId = :productId and i.warehouse.id = :warehouseId and i.availableQuantity >= :quantity")
    int reserveQuantity(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InventoryItem i set i.availableQuantity = i.availableQuantity + :quantity, i.reservedQuantity = i.reservedQuantity - :quantity, i.updatedAt = current_timestamp where i.productId = :productId and i.warehouse.id = :warehouseId and i.reservedQuantity >= :quantity")
    int releaseQuantity(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update InventoryItem i set i.reservedQuantity = i.reservedQuantity - :quantity, i.updatedAt = current_timestamp where i.productId = :productId and i.warehouse.id = :warehouseId and i.reservedQuantity >= :quantity")
    int consumeReservedQuantity(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId, @Param("quantity") int quantity);

    @Query("select coalesce(sum(i.availableQuantity), 0) from InventoryItem i where i.productId = :productId")
    long sumAvailableByProductId(@Param("productId") UUID productId);
}
