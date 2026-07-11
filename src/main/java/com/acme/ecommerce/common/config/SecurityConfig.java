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

/**
 * Central HTTP security configuration for the demo API.
 *
 * <p>There is intentionally no API gateway. The application accepts a simple
 * {@code Authorization: Bearer <token>} header and authorizes endpoints by role.
 * Example: product admins can maintain categories and coupons, but cannot create
 * seller products or place customer orders.</p>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private static final String COUPON_ENROLLMENT = ApiPaths.API_V1 + "/coupons/*/products/*/enroll";
    private static final String PRODUCT_DETAILS = ApiPaths.API_V1 + "/products/*";
    private static final String PRODUCT_VERSIONS = ApiPaths.API_V1 + "/products/*/versions";

    private final TokenAuthenticationFilter tokenAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/dashboard", "/admin", "/seller", "/customer", "/signup", "/login", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .requestMatchers(ApiPaths.AUTH, ApiPaths.ACTUATOR).permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPaths.CATEGORIES, ApiPaths.SEARCH, PRODUCT_DETAILS).permitAll()

                        .requestMatchers(HttpMethod.POST, COUPON_ENROLLMENT).hasRole(UserRole.SELLER.name())
                        .requestMatchers(HttpMethod.DELETE, COUPON_ENROLLMENT).hasRole(UserRole.SELLER.name())

                        .requestMatchers(HttpMethod.POST, ApiPaths.CATEGORIES).hasRole(UserRole.PRODUCT_ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, ApiPaths.CATEGORIES).hasRole(UserRole.PRODUCT_ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, ApiPaths.CATEGORIES).hasRole(UserRole.PRODUCT_ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, ApiPaths.CATEGORIES).hasRole(UserRole.PRODUCT_ADMIN.name())

                        .requestMatchers(HttpMethod.POST, ApiPaths.COUPONS).hasRole(UserRole.PRODUCT_ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, ApiPaths.COUPONS).hasRole(UserRole.PRODUCT_ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, ApiPaths.COUPONS).hasRole(UserRole.PRODUCT_ADMIN.name())
                        .requestMatchers(HttpMethod.GET, ApiPaths.COUPONS).hasAnyRole(UserRole.PRODUCT_ADMIN.name(), UserRole.SELLER.name(), UserRole.CUSTOMER.name())

                        .requestMatchers(HttpMethod.GET, PRODUCT_VERSIONS).hasRole(UserRole.SELLER.name())
                        .requestMatchers(ApiPaths.SELLERS, ApiPaths.INVENTORY).hasRole(UserRole.SELLER.name())
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
