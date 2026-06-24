package com.acme.ecommerce.auth.controller;

import com.acme.ecommerce.auth.dto.AuthResponse;
import com.acme.ecommerce.auth.dto.CustomerSignupRequest;
import com.acme.ecommerce.auth.dto.LoginRequest;
import com.acme.ecommerce.auth.dto.SellerSignupRequest;
import com.acme.ecommerce.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/sellers/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signupSeller(@Valid @RequestBody SellerSignupRequest request) {
        return authService.signupSeller(request);
    }

    @PostMapping("/customers/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signupCustomer(@Valid @RequestBody CustomerSignupRequest request) {
        return authService.signupCustomer(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
