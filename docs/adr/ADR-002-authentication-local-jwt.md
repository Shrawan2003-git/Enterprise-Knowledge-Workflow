# ADR-002: Authentication — Local JWT (OIDC-Ready)

**Status:** Accepted  
**Date:** 2026-10-29

---

## Context

KnowFlow needs to authenticate users and authorize API requests. The system is initially
deployed as an internal enterprise tool without an existing corporate Identity Provider (IdP)
to integrate with. Requirements:

1. Stateless request authentication for the REST API.
2. Secure password storage.
3. Token refresh without requiring re-login for reasonable session durations.
4. Ability to migrate to OIDC/SSO when the organization adopts a corporate IdP.
5. No CSRF vulnerability surface.
6. The acting user must always come from `SecurityContext` — never from request bodies or
   model output (Architecture Rule #9).

## Decision

### Authentication Flow

```
┌──────────┐   POST /api/auth/login     ┌──────────────┐
│  Client   │ ──────────────────────────▶│  Auth        │
│ (React)   │   {email, password}        │  Controller  │
│           │ ◀──────────────────────────│              │
│           │   {accessToken,            └──────┬───────┘
│           │    refreshToken,                  │
│           │    expiresIn}                     ▼
│           │                            ┌──────────────┐
│           │   Authorization: Bearer    │  Auth        │
│           │ ──────────────────────────▶│  Service     │
│           │                            │ (interface)  │
└──────────┘                            └──────────────┘
```

### Password Handling

| Aspect | Decision |
|--------|----------|
| Hashing algorithm | BCrypt (Spring Security default, cost factor 10) |
| Storage | `password_hash` column in `users` table |
| Validation | Jakarta Validation on input; min 8 chars, max 128 chars |
| Failed login tracking | Counter in `users` table; account locked after 5 consecutive failures; auto-unlock after 30 minutes |

### Access Tokens (JWT)

| Property | Value |
|----------|-------|
| Format | Signed JWT (JWS) |
| Algorithm | HMAC-SHA256 (HS256) |
| Signing key | From environment variable `KNOWFLOW_JWT_SECRET` (min 256 bits) |
| Lifetime | 15 minutes |
| Claims | `sub` (user UUID), `email`, `roles` (list), `departmentId`, `iat`, `exp`, `jti` |
| Transport | `Authorization: Bearer <token>` header |
| Storage (client) | In-memory only (JavaScript variable). Never `localStorage`. |

### Refresh Tokens

| Property | Value |
|----------|-------|
| Format | Opaque random string (64 bytes, Base64URL-encoded) |
| Storage (server) | SHA-256 hash in `refresh_tokens` table with `user_id`, `expires_at`, `revoked_at`, `device_info` |
| Lifetime | 7 days |
| Rotation | Every use issues a new refresh token and revokes the old one |
| Revocation | On logout, on password change, on admin force-revoke |
| Family tracking | `token_family` UUID; if a revoked token is reused, the entire family is revoked (replay detection) |
| Transport | HTTP-only, Secure, SameSite=Strict cookie on `/api/auth/refresh` path |

### CSRF Protection

**CSRF tokens are NOT used.** Justification:

- Access tokens are sent in the `Authorization` header, which cannot be set by cross-origin
  forms or image tags.
- Refresh tokens are in HTTP-only cookies scoped to `/api/auth/refresh` with `SameSite=Strict`,
  which prevents cross-site attachment.
- No cookie-based session authentication exists in the system.

This is a deliberate, documented decision — not an oversight.

### OIDC Migration Path

Authentication logic is behind an `AuthenticationService` interface:

```java
public interface AuthenticationService {
    AuthTokens authenticate(LoginRequest request);
    AuthTokens refresh(String refreshToken);
    void logout(String refreshToken);
    void revokeAllTokens(UUID userId);
}
```

The current implementation is `LocalAuthenticationService`. When OIDC is needed:

1. Implement `OidcAuthenticationService` that delegates to Spring Security OAuth2 Client.
2. Swap via Spring profile (`spring.profiles.active=oidc`).
3. Access token validation switches from local JWT verification to OIDC token introspection
   or JWKS-based JWT validation.
4. The `password_hash` column and refresh token table become unused (but remain for rollback).

### Security Headers

All responses include:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Strict-Transport-Security: max-age=31536000; includeSubDomains (when TLS)
Cache-Control: no-store (on auth endpoints)
```

## Alternatives Considered

### Session-Based Authentication (HttpSession + Cookies)

| Aspect | Assessment |
|--------|------------|
| Pros | Simple, built into Spring Security, automatic CSRF protection with session cookies |
| Cons | Requires sticky sessions or shared session store for horizontal scaling. Harder to support API-first clients (mobile, CLI). Session storage grows with active users. |
| Verdict | **Rejected.** Stateless JWT is simpler for an API-first architecture and avoids session store management. |

### OIDC/SSO Only (No Local Auth)

| Aspect | Assessment |
|--------|------------|
| Pros | Delegates identity management to a dedicated IdP. Better security posture long-term. |
| Cons | Requires an enterprise IdP (Keycloak, Okta, Azure AD) to be available from day 1. Adds deployment complexity for initial development and testing. |
| Verdict | **Rejected for initial release.** The `AuthenticationService` interface ensures we can adopt OIDC later without changing any downstream code. |

### Opaque Access Tokens (Non-JWT)

| Aspect | Assessment |
|--------|------------|
| Pros | Token introspection allows instant revocation. No sensitive data in the token. |
| Cons | Every API request requires a database lookup to validate the token. Adds latency and DB load. |
| Verdict | **Rejected.** Short-lived JWTs (15 min) provide an acceptable revocation window. For immediate revocation needs (compromised account), we revoke all refresh tokens and the short access token lifetime limits exposure. |

## Consequences

### Positive

- **Stateless API:** No server-side session state. Horizontal scaling is trivial.
- **Fast validation:** JWT signature verification is a local CPU operation — no DB call per
  request.
- **Migration-ready:** `AuthenticationService` interface decouples all business logic from the
  auth mechanism.
- **Replay-resistant:** Refresh token family tracking detects and blocks token replay attacks.

### Negative

- **15-minute revocation window:** A compromised access token is valid until it expires.
  Mitigation: short lifetime + refresh token revocation cascade.
- **Secret management:** The JWT signing key must be managed as a secret in the deployment
  environment. Rotation requires coordinated deployment.
- **Password responsibility:** We own password hashing, storage, and policy. This is a
  liability that goes away with OIDC migration.

### Obligations

- [ ] Validate `KNOWFLOW_JWT_SECRET` at startup: reject if missing or < 32 bytes.
- [ ] Write integration tests for: login, refresh, refresh rotation, replay detection,
      account lockout, logout.
- [ ] Never log tokens, passwords, or the signing key (Architecture Rule #10).
- [ ] Add rate limiting on `/api/auth/login` (429 after 10 requests/minute per IP).

---

*Cross-references:* [ADR-001](ADR-001-modular-monolith-architecture.md) (module structure),
[threat-model.md](../threat-model.md) (authentication threats),
[api-reference.md](../api-reference.md) (auth endpoints)
