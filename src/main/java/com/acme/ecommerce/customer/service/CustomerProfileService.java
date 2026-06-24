package com.acme.ecommerce.customer.service;

import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerProfileService {
    private final CustomerProfileRepository customerProfileRepository;

    public CustomerProfile requireByUserId(UUID userId) {
        return customerProfileRepository.findByUserAccountId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }
}
