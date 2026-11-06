package com.knowflow.identity;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthenticationService {
    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);
    AuthResponse refresh(String refreshToken);
    void logout(String refreshToken);
}
