package com.knowflow.identity;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserDto user
) {}
