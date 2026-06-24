package com.acme.ecommerce.auth;

import com.acme.ecommerce.auth.dto.AuthResponse;
import com.acme.ecommerce.auth.dto.CustomerSignupRequest;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.entity.AuthToken;
import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.repository.UserAccountRepository;
import com.acme.ecommerce.auth.service.AuthService;
import com.acme.ecommerce.auth.service.TokenAuthenticationService;
import com.acme.ecommerce.auth.validation.AuthRequestValidator;
import com.acme.ecommerce.customer.repository.CustomerProfileRepository;
import com.acme.ecommerce.seller.repository.SellerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private SellerProfileRepository sellerProfileRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenAuthenticationService tokenAuthenticationService;

    private final AuthRequestValidator authRequestValidator = new AuthRequestValidator();

    private AuthService authService;

    @Test
    void sellerSignupCreatesSellerRoleToken() {
        authService = new AuthService(userAccountRepository, sellerProfileRepository, customerProfileRepository, passwordEncoder, tokenAuthenticationService, authRequestValidator);
        when(userAccountRepository.existsByEmailIgnoreCase("seller@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });
        when(tokenAuthenticationService.issueToken(any(UserAccount.class))).thenAnswer(invocation -> token(invocation.getArgument(0)));

        AuthResponse response = authService.signupSeller(new SellerSignupRequest("seller@example.com", "Password1", "Acme", null));

        assertThat(response.role()).isEqualTo(UserRole.SELLER);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void customerSignupCreatesCustomerRoleToken() {
        authService = new AuthService(userAccountRepository, sellerProfileRepository, customerProfileRepository, passwordEncoder, tokenAuthenticationService, authRequestValidator);
        when(userAccountRepository.existsByEmailIgnoreCase("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("hash");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setId(UUID.randomUUID());
            return account;
        });
        when(tokenAuthenticationService.issueToken(any(UserAccount.class))).thenAnswer(invocation -> token(invocation.getArgument(0)));

        AuthResponse response = authService.signupCustomer(new CustomerSignupRequest("customer@example.com", "Password1", "Customer One", null));

        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    private AuthToken token(UserAccount account) {
        AuthToken token = new AuthToken();
        token.setToken("token-value");
        token.setUserAccount(account);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }
}
