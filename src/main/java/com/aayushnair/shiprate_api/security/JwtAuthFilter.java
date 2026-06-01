package com.aayushnair.shiprate_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    // OncePerRequestFilter guarantees this filter runs exactly once per request
    // even in async or forward scenarios (e.g. Spring's /error forwarding)

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Only process requests with a Bearer token
        // Requests without a token pass through — SecurityConfig decides
        // whether the route requires authentication
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // strip "Bearer " prefix

            if (jwtUtil.isTokenValid(token)) {
                String username = jwtUtil.extractUsername(token);

                // Set authentication in SecurityContext so downstream filters
                // and controllers know who the caller is
                // Empty authorities list — no role-based access control in this project
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                username, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            // Invalid token → authentication not set → request rejected by AuthorizationFilter
        }

        // Always continue the filter chain — security decisions are made downstream
        // by Spring Security's AuthorizationFilter, not here
        filterChain.doFilter(request, response);
    }
}