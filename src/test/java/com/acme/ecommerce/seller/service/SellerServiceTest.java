package com.acme.ecommerce.seller.service;

import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.common.exception.ResourceNotFoundException;
import com.acme.ecommerce.seller.dto.SellerProfileResponse;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.repository.SellerProfileRepository;
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

/** Unit specification for seller profile lookup and response shaping. */
@ExtendWith(MockitoExtension.class)
class SellerServiceTest {

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @InjectMocks
    private SellerService sellerService;

    @Test
    void requireByUserId_shouldReturnSellerProfile_whenProfileExists() {
        // Given
        UUID userId = UUID.randomUUID();
        SellerProfile profile = sellerProfile(UUID.randomUUID(), userId);
        when(sellerProfileRepository.findByUserAccountId(userId)).thenReturn(Optional.of(profile));

        // When
        SellerProfile response = sellerService.requireByUserId(userId);

        // Then
        assertThat(response).isSameAs(profile);
    }

    @Test
    void requireByUserId_shouldThrowResourceNotFoundException_whenProfileDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        when(sellerProfileRepository.findByUserAccountId(userId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> sellerService.requireByUserId(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Seller profile not found");
    }

    @Test
    void getMyProfile_shouldReturnSellerProfileResponse_whenProfileExists() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        SellerProfile profile = sellerProfile(sellerId, userId);
        when(sellerProfileRepository.findByUserAccountId(userId)).thenReturn(Optional.of(profile));

        // When
        SellerProfileResponse response = sellerService.getMyProfile(userId);

        // Then
        assertThat(response.sellerId()).isEqualTo(sellerId);
        assertThat(response.email()).isEqualTo("seller@example.com");
        assertThat(response.businessName()).isEqualTo("Acme Seller");
    }

    private SellerProfile sellerProfile(UUID sellerId, UUID userId) {
        UserAccount user = new UserAccount();
        user.setId(userId);
        user.setEmail("seller@example.com");

        SellerProfile profile = new SellerProfile();
        profile.setId(sellerId);
        profile.setUserAccount(user);
        profile.setBusinessName("Acme Seller");
        profile.setContactNumber("9999999999");
        return profile;
    }
}
