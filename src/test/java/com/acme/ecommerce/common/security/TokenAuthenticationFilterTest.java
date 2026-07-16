package com.acme.ecommerce.common.security;

import com.acme.ecommerce.auth.enums.UserRole;
import com.acme.ecommerce.auth.service.TokenAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit specification for the bearer-token security filter.
 *
 * <p>Controller specs disable the full filter chain, so this focused unit test verifies the
 * production filter behavior once without forcing every controller test through authentication.</p>
 */
@ExtendWith(MockitoExtension.class)
class TokenAuthenticationFilterTest {

    @Mock
    private TokenAuthenticationService tokenAuthenticationService;

    @Mock
    private FilterChain filterChain;

    private TokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TokenAuthenticationFilter(tokenAuthenticationService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldSetSecurityContextAuthentication_whenBearerTokenIsValid() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "seller@example.com", UserRole.SELLER);
        when(tokenAuthenticationService.authenticate("valid-token")).thenReturn(Optional.of(user));

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(user);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SELLER");

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldLeaveSecurityContextEmpty_whenAuthorizationHeaderIsMissing() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(tokenAuthenticationService.authenticate(null)).thenReturn(Optional.empty());

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldTrimBearerTokenBeforeAuthenticating() throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer   spaced-token   ");
        when(tokenAuthenticationService.authenticate("spaced-token")).thenReturn(Optional.empty());

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(tokenAuthenticationService).authenticate("spaced-token");
        verify(filterChain).doFilter(request, response);
    }
}
