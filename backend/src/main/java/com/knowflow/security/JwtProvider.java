package com.knowflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtProvider(
            @Value("${knowflow.security.jwt.secret}") String secret,
            @Value("${knowflow.security.jwt.access-token-expiration-ms:900000}") long accessTokenExpirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    public String generateAccessToken(CurrentUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.email());
        claims.put("displayName", user.displayName());
        claims.put("roles", user.roles());
        if (user.departmentId() != null) {
            claims.put("departmentId", user.departmentId().toString());
        }

        return Jwts.builder()
                .subject(user.id().toString())
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public CurrentUser parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String displayName = claims.get("displayName", String.class);
            String deptIdStr = claims.get("departmentId", String.class);
            UUID departmentId = deptIdStr != null ? UUID.fromString(deptIdStr) : null;

            @SuppressWarnings("unchecked")
            List<String> roleList = claims.get("roles", List.class);
            Set<String> roles = roleList != null ? new HashSet<>(roleList) : Set.of();

            return new CurrentUser(userId, email, displayName, departmentId, roles);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
