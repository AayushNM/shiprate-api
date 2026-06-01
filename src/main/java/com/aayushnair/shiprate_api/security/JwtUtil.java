package com.aayushnair.shiprate_api.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // Caffeine in-process cache: token string → parsed Claims object
    // Eliminates repeated cryptographic verification on every authenticated request
    // Same pattern as FedEx Okta JWT pre-caching — cache the result, not the token fetch
    private Cache<String, Claims> claimsCache;

    // @PostConstruct runs once after the bean is fully initialized
    // Used here instead of the constructor because @Value fields aren't
    // injected yet when the constructor runs
    @PostConstruct
    public void initCache() {
        claimsCache = Caffeine.newBuilder()
                // Entries expire 10 minutes after being written
                // Well within the 24-hour token TTL — cache stays fresh
                .expireAfterWrite(10, TimeUnit.MINUTES)
                // Cap at 1000 entries to prevent unbounded memory growth
                // At 1 entry per active session, this handles 1000 concurrent users
                .recordStats() // enables hit/miss tracking via /actuator/metrics
                .maximumSize(1_000)
                .build();
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                // Token expires after configured TTL (24 hours in dev)
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Catches expired tokens, invalid signatures, malformed tokens
            return false;
        }
    }

    private Claims parseClaims(String token) {
        // Cache hit → nanosecond HashMap lookup, no crypto work
        // Cache miss → full cryptographic parse, result stored for future requests
        return claimsCache.get(token, t ->
                Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(t)
                        .getPayload()
        );
    }

    private SecretKey getSigningKey() {
        // Decode the Base64 secret from application.yaml into raw bytes
        // then wrap as an HMAC-SHA key for signing and verification
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}