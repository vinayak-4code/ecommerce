package com.acme.ecommerce.seller.controller;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.common.security.AuthenticatedUser;
import com.acme.ecommerce.seller.dto.SellerProfileResponse;
import com.acme.ecommerce.seller.service.SellerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for seller profile endpoint.
 */
@ExtendWith(MockitoExtension.class)
class SellerControllerTest {

    @Mock
    private SellerService sellerService;

    @InjectMocks
    private SellerController sellerController;

    private UUID sellerUserId;

    @BeforeEach
    void setUp() {
        sellerUserId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedUser(sellerUserId, "seller@example.com", UserRole.SELLER), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void me_shouldReturnCurrentSellerProfile_whenUserIsAuthenticated() {
        // Given
        SellerProfileResponse expected = new SellerProfileResponse(UUID.randomUUID(), "seller@example.com", "Acme Seller", "555");
        when(sellerService.getMyProfile(sellerUserId)).thenReturn(expected);

        // When
        SellerProfileResponse response = sellerController.me();

        // Then
        assertThat(response).isSameAs(expected);
        verify(sellerService).getMyProfile(sellerUserId);
    }
}
