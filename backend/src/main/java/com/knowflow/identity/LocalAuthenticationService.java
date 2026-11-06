package com.knowflow.identity;

import com.knowflow.audit.AuditService;
import com.knowflow.security.CurrentUser;
import com.knowflow.security.JwtProvider;
import com.knowflow.security.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocalAuthenticationService implements AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginRateLimiter rateLimiter;
    private final AuditService auditService;
    private final Clock clock;
    private final long refreshTokenExpirationMs;

    public LocalAuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            LoginRateLimiter rateLimiter,
            AuditService auditService,
            Clock clock,
            @Value("${knowflow.security.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.clock = clock;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Override
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest != null ? httpRequest.getRemoteAddr() : "unknown";
        String rateLimitKey = clientIp + ":" + request.email().toLowerCase().trim();

        if (!rateLimiter.tryAcquire(rateLimitKey)) {
            auditService.record(null, "LOGIN_RATE_LIMITED", "USER", null, Map.of("email", request.email(), "ip", clientIp));
            throw new BadCredentialsException("Too many login attempts. Please try again later.");
        }

        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElse(null);

        if (user == null || !user.isActive() || user.getPasswordHash() == null ||
                !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(user != null ? user.getId() : null, "LOGIN_FAILURE", "USER", user != null ? user.getId() : null, Map.of("email", request.email()));
            throw new BadCredentialsException("Invalid email or password");
        }

        rateLimiter.reset(rateLimitKey);

        CurrentUser currentUser = toCurrentUser(user);
        String accessToken = jwtProvider.generateAccessToken(currentUser);
        String rawRefreshToken = generateSecureRandomToken();
        String hashedRefreshToken = hashToken(rawRefreshToken);

        Instant expiresAt = Instant.now(clock).plusMillis(refreshTokenExpirationMs);
        RefreshToken tokenEntity = new RefreshToken(user.getId(), hashedRefreshToken, expiresAt);
        refreshTokenRepository.save(tokenEntity);

        auditService.record(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), Map.of("email", user.getEmail()));

        return new AuthResponse(accessToken, rawRefreshToken, 900, UserDto.fromEntity(user));
    }

    @Override
    public AuthResponse refresh(String rawRefreshToken) {
        String hashed = hashToken(rawRefreshToken);
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hashed)
                .orElseThrow(() -> new BadCredentialsException("Invalid or revoked refresh token"));

        if (tokenEntity.isRevoked() || tokenEntity.isExpired(Instant.now(clock))) {
            tokenEntity.setRevoked(true);
            refreshTokenRepository.save(tokenEntity);
            throw new BadCredentialsException("Refresh token has expired or is revoked");
        }

        // Token rotation: Revoke used token and issue a fresh one
        tokenEntity.setRevoked(true);
        refreshTokenRepository.save(tokenEntity);

        User user = userRepository.findById(tokenEntity.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User account not found"));

        if (!user.isActive()) {
            throw new BadCredentialsException("User account is inactive");
        }

        CurrentUser currentUser = toCurrentUser(user);
        String newAccessToken = jwtProvider.generateAccessToken(currentUser);
        String newRawRefreshToken = generateSecureRandomToken();
        String newHashedRefreshToken = hashToken(newRawRefreshToken);

        Instant expiresAt = Instant.now(clock).plusMillis(refreshTokenExpirationMs);
        RefreshToken newTokenEntity = new RefreshToken(user.getId(), newHashedRefreshToken, expiresAt);
        refreshTokenRepository.save(newTokenEntity);

        return new AuthResponse(newAccessToken, newRawRefreshToken, 900, UserDto.fromEntity(user));
    }

    @Override
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.trim().isEmpty()) {
            String hashed = hashToken(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(hashed).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
                auditService.record(token.getUserId(), "LOGOUT", "USER", token.getUserId(), Map.of());
            });
        }
    }

    private CurrentUser toCurrentUser(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        return new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getDepartmentId(), roleNames);
    }

    private String generateSecureRandomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
