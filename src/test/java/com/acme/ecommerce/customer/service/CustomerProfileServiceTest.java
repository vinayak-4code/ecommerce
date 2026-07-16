package com.acme.ecommerce.customer.service;

import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.repository.CustomerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Unit specification for resolving customer profiles from authenticated user ids. */
@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @InjectMocks
    private CustomerProfileService customerProfileService;

    @Test
    void requireByUserId_shouldReturnCustomerProfile_whenProfileExists() {
        // Given
        UUID userId = UUID.randomUUID();
        CustomerProfile profile = new CustomerProfile();
        profile.setId(UUID.randomUUID());
        when(customerProfileRepository.findByUserAccountId(userId)).thenReturn(Optional.of(profile));

        // When
        CustomerProfile response = customerProfileService.requireByUserId(userId);

        // Then
        assertThat(response).isSameAs(profile);
    }

    @Test
    void requireByUserId_shouldThrowResourceNotFoundException_whenProfileDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        when(customerProfileRepository.findByUserAccountId(userId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> customerProfileService.requireByUserId(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Customer profile not found");
    }
}
