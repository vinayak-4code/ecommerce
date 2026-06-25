package com.acme.ecommerce.customer.service;

import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Small customer-profile lookup service used by cart and order modules.
 * Example: a Customer Bearer token contains a user id, and this service resolves
 * the customer profile that owns carts/orders.
 */
@Service
@RequiredArgsConstructor
public class CustomerProfileService {
    private final CustomerProfileRepository customerProfileRepository;

    /**
     * Loads a customer profile for the authenticated user id.
     */
    @Transactional(readOnly = true)
    public CustomerProfile requireByUserId(UUID userId) {
        return customerProfileRepository.findByUserAccountId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }
}
