package com.acme.ecommerce.common.config;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.common.security.TokenAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final TokenAuthenticationFilter tokenAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.AUTH, ApiPaths.ACTUATOR).permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.CATEGORIES, ApiPaths.PRODUCTS, ApiPaths.SEARCH).permitAll()
                        .requestMatchers(HttpMethod.POST, ApiPaths.CATEGORIES).hasRole(UserRole.SELLER.name())
                        .requestMatchers(ApiPaths.SELLERS, ApiPaths.INVENTORY).hasRole(UserRole.SELLER.name())
                        .requestMatchers(HttpMethod.POST, ApiPaths.COUPONS).hasRole(UserRole.SELLER.name())
                        .requestMatchers(HttpMethod.POST, ApiPaths.PRODUCTS).hasRole(UserRole.SELLER.name())
                        .requestMatchers(HttpMethod.PUT, ApiPaths.PRODUCTS).hasRole(UserRole.SELLER.name())
                        .requestMatchers(HttpMethod.PATCH, ApiPaths.PRODUCTS).hasRole(UserRole.SELLER.name())
                        .requestMatchers(HttpMethod.DELETE, ApiPaths.PRODUCTS).hasRole(UserRole.SELLER.name())
                        .requestMatchers(ApiPaths.CART, ApiPaths.ORDERS).hasRole(UserRole.CUSTOMER.name())
                        .anyRequest().authenticated()
                )
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
