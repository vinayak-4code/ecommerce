package com.acme.ecommerce.order.repository;

import com.acme.ecommerce.order.entity.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {
    Optional<CustomerOrder> findByIdAndCustomerProfileId(UUID id, UUID customerId);

    List<CustomerOrder> findByCustomerProfileIdOrderByCreatedAtDesc(UUID customerId);
}
