package com.aayushnair.shiprate_api.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // REST APIs are stateless and use JWT — CSRF protection is for browser
                // session-based apps where a malicious site can trick a logged-in user
                // into submitting a form. Since every request must carry a JWT,
                // CSRF adds no security benefit here.
                .csrf(csrf -> csrf.disable())

                // Never create an HTTP session — every request must carry its own JWT
                // Sessions belong to server-rendered web apps, not REST APIs
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Return 401 Unauthorized for unauthenticated requests instead of
                // Spring Security 6's default 403 Forbidden — 401 is the correct
                // HTTP semantic for "you need to authenticate first"
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
                )

                .authorizeHttpRequests(auth -> auth
                        // Public routes — no token required
                        // Using AntPathRequestMatcher explicitly to bypass Spring Security 6's
                        // MVC-based path matching which caused 403s on permitAll() routes
                        .requestMatchers(
                                new AntPathRequestMatcher("/auth/login"),
                                new AntPathRequestMatcher("/api/health"),
                                new AntPathRequestMatcher("/v3/api-docs/**"),
                                new AntPathRequestMatcher("/swagger-ui/**"),
                                new AntPathRequestMatcher("/swagger-ui.html")
                        ).permitAll()
                        // Everything else requires a valid JWT — safe default, opt routes
                        // OUT of security explicitly rather than accidentally leaving
                        // something unprotected
                        .anyRequest().authenticated()
                )

                // Insert JWT filter BEFORE Spring's default username/password filter
                // so token validation happens first on every authenticated request
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt is the industry standard for password hashing
        // It's intentionally slow (adaptive cost factor) to resist brute-force attacks
        // Never use MD5, SHA-1, or plain SHA-256 for passwords
        return new BCryptPasswordEncoder();
    }
}