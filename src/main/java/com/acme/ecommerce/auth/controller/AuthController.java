package com.acme.ecommerce.auth.controller;

import com.acme.ecommerce.auth.dto.AuthResponse;
import com.acme.ecommerce.auth.dto.CustomerSignupRequest;
import com.acme.ecommerce.auth.dto.LoginRequest;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication entry point for sellers, customers, and seeded product admins.
 *
 * <p>Example: sellers and customers use dedicated signup APIs, while product
 * admins are seeded for demo governance and login through the common login API
 * using {@code role=PRODUCT_ADMIN}.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * Registers a seller and creates the matching seller profile.
     * Example request: {"email":"seller@example.com","password":"Password1","businessName":"Acme Seller"}.
     */
    @PostMapping("/sellers/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signupSeller(@Valid @RequestBody SellerSignupRequest request) {
        return authService.signupSeller(request);
    }

    /**
     * Registers a customer and creates the matching customer profile and login token.
     * Example request: {"email":"customer@example.com","password":"Password1","fullName":"Demo Customer"}.
     */
    @PostMapping("/customers/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signupCustomer(@Valid @RequestBody CustomerSignupRequest request) {
        return authService.signupCustomer(request);
    }

    /**
     * Authenticates a Product Admin, Seller, or Customer using an explicit role.
     * Example request: {"email":"admin@example.com","password":"Password1","role":"PRODUCT_ADMIN"}.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Revokes the current Bearer token when the caller provides an Authorization header.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authService.logout(authorizationHeader);
    }
}
