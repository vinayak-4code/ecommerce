package com.acme.ecommerce.auth.service;

import com.acme.ecommerce.auth.dto.AuthResponse;
import com.acme.ecommerce.auth.dto.CustomerSignupRequest;
import com.acme.ecommerce.auth.dto.LoginRequest;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.entity.AuthToken;
import com.acme.ecommerce.auth.entity.UserAccount;
import com.acme.ecommerce.auth.enums.AccountStatus;
import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.repository.UserAccountRepository;
import com.acme.ecommerce.auth.validation.AuthRequestValidator;
import com.acme.ecommerce.common.exception.DuplicateResourceException;
import com.acme.ecommerce.common.exception.ForbiddenOperationException;
import com.acme.ecommerce.customer.entity.CustomerProfile;
import com.acme.ecommerce.customer.repository.CustomerProfileRepository;
import com.acme.ecommerce.seller.entity.SellerProfile;
import com.acme.ecommerce.seller.repository.SellerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String TOKEN_TYPE = "Bearer";

    private final UserAccountRepository userAccountRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenAuthenticationService tokenAuthenticationService;
    private final AuthRequestValidator authRequestValidator;

    @Transactional
    public AuthResponse signupSeller(SellerSignupRequest request) {
        authRequestValidator.validatePassword(request.password());
        UserAccount userAccount = createUser(request.email(), request.password(), UserRole.SELLER);

        SellerProfile profile = new SellerProfile();
        profile.setUserAccount(userAccount);
        profile.setBusinessName(request.businessName().trim());
        profile.setContactNumber(request.contactNumber());
        sellerProfileRepository.save(profile);

        return issueResponse(userAccount);
    }

    @Transactional
    public AuthResponse signupCustomer(CustomerSignupRequest request) {
        authRequestValidator.validatePassword(request.password());
        UserAccount userAccount = createUser(request.email(), request.password(), UserRole.CUSTOMER);

        CustomerProfile profile = new CustomerProfile();
        profile.setUserAccount(userAccount);
        profile.setFullName(request.fullName().trim());
        profile.setPhoneNumber(request.phoneNumber());
        customerProfileRepository.save(profile);

        return issueResponse(userAccount);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = authRequestValidator.normalizeEmail(request.email());
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (userAccount.getRole() != request.role()) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (userAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new ForbiddenOperationException("Account is not active");
        }
        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return issueResponse(userAccount);
    }

    private UserAccount createUser(String email, String password, UserRole role) {
        String normalizedEmail = authRequestValidator.normalizeEmail(email);
        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(normalizedEmail);
        userAccount.setPasswordHash(passwordEncoder.encode(password));
        userAccount.setRole(role);
        userAccount.setStatus(AccountStatus.ACTIVE);
        return userAccountRepository.save(userAccount);
    }

    private AuthResponse issueResponse(UserAccount userAccount) {
        AuthToken token = tokenAuthenticationService.issueToken(userAccount);
        return new AuthResponse(TOKEN_TYPE, token.getToken(), token.getExpiresAt(), userAccount.getId(), userAccount.getRole());
    }
}
