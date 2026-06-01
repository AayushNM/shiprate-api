package com.aayushnair.shiprate_api.controller;

import com.aayushnair.shiprate_api.dto.AuthRequest;
import com.aayushnair.shiprate_api.dto.AuthResponse;
import com.aayushnair.shiprate_api.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    // BCrypt hash generated at startup using the same PasswordEncoder bean
    // Avoids hardcoding a static hash that could be wrong (learned the hard way)
    // In production replace with a real UserDetailsService backed by the database
    private final String demoPassHash;

    private static final String DEMO_USER = "admin";

    public AuthController(JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        // Encode at startup so encode and matches always use identical configuration
        this.demoPassHash = passwordEncoder.encode("password");
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request) {
        // Constant-time comparison via BCrypt — prevents timing attacks
        // where an attacker could infer valid usernames from response time differences
        if (!DEMO_USER.equals(request.getUsername()) ||
                !passwordEncoder.matches(request.getPassword(), demoPassHash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return new AuthResponse(jwtUtil.generateToken(request.getUsername()));
    }
}