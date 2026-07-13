package com.acme.ecommerce.order.repository;

import com.acme.ecommerce.order.entity.OrderLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {
    List<OrderLine> findByOrderId(UUID orderId);

    Optional<OrderLine> findByIdAndProductIdIn(UUID id, Collection<UUID> productIds);

    @Query(value = "select l from OrderLine l join l.order o where l.productId in :productIds order by o.createdAt desc",
            countQuery = "select count(l) from OrderLine l where l.productId in :productIds")
    Page<OrderLine> findSellerLines(@Param("productIds") Collection<UUID> productIds, Pageable pageable);
}
