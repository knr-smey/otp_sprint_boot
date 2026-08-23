# Implement Google Authenticator 2FA (TOTP) in this existing Spring Boot application

## CLARIFICATION ON "GOOGLE OTP" — READ FIRST

This feature uses the **Google Authenticator mobile app** with the **TOTP standard (RFC 6238)**.

- Do **NOT** call any Google API, Google Cloud service, Google Identity/OAuth, or Firebase.
- Do **NOT** send OTP by SMS or email.
- The server generates a shared secret. The user scans it as a QR code with Google Authenticator (or Authy, Microsoft Authenticator, 1Password — all compatible). The app then generates a 6-digit code every 30 seconds, offline, from `HMAC-SHA1(secret, current_time_step)`.
- The server independently computes the same code and compares it.
- This is exactly what Laravel's `pragmarx/google2fa` package does. Implement the Spring Boot equivalent.

Laravel → Spring Boot mapping for reference:

| Laravel (`pragmarx/google2fa`) | Spring Boot equivalent |
| --- | --- |
| `Google2FA::generateSecretKey()` | `SecretGenerator.generate()` |
| `Google2FA::getQRCodeUrl()` | `QrData.Builder` → `otpauth://` URI |
| `Google2FA::verifyKey($secret, $code)` | `CodeVerifier.isValidCode()` |
| `google2fa.php` config | `application.yml` properties |

---

## IMPORTANT — BEFORE WRITING ANY CODE

- First inspect the existing project structure, authentication flow, Spring Security configuration, User entity, database schema, JWT/session implementation, login controller, services, and frontend/API flow.
- Do NOT rewrite or replace the existing authentication system.
- Reuse the existing architecture and coding patterns.
- Before modifying code, explain briefly which files/classes need to change and why.
- Keep the implementation production-ready and backward compatible.

---

## GOAL

Add Google Authenticator-based Two-Factor Authentication using TOTP.

## AUTHENTICATION FLOW

1. **Normal login:** `POST /auth/login` with username + password.
2. **If password is invalid:** return the existing authentication error.
3. **If password is valid and 2FA is disabled:** continue the existing login flow, return the normal JWT/session response.
4. **If password is valid and 2FA is enabled:**
   - DO NOT issue the final access token yet.
   - Return a temporary authentication result indicating that 2FA is required.
   - Use a secure server-side temporary token/state, not a client-side boolean.
5. **Frontend then submits:** `POST /auth/2fa/verify`
   ```json
   { "temporaryToken": "...", "code": "123456" }
   ```
6. Verify the 6-digit TOTP code using the user's stored TOTP secret.
7. **If valid:** complete authentication, issue the normal JWT/access token and refresh token if the existing system uses them.
8. **If invalid:** return an appropriate authentication error. Do not issue an access token.

## 2FA SETUP FLOW

### `POST /auth/2fa/setup` (authenticated)
- Generate a cryptographically secure TOTP secret (Base32, 160-bit / 32 chars).
- Generate an `otpauth://` URI compatible with Google Authenticator:
  `otpauth://totp/{Issuer}:{account}?secret={SECRET}&issuer={Issuer}&algorithm=SHA1&digits=6&period=30`
- Return the secret / QR-code data needed by the frontend.
- DO NOT enable 2FA yet. Hold the pending secret server-side (temporary storage or a `pending_secret` column), never trust the client to send it back.

### `POST /auth/2fa/enable`
```json
{ "code": "123456" }
```
- Verify the TOTP code against the newly generated pending secret.
- Only after successful verification: persist the encrypted TOTP secret and set `twoFactorEnabled = true`.
- If verification fails, do not enable 2FA.

### `POST /auth/2fa/disable`
- Require the user to be authenticated.
- Require a valid TOTP code (or backup code) before disabling.
- Never allow an unauthenticated request to disable 2FA.
- Wipe the stored secret and backup codes on disable.

## DATABASE

Inspect the existing User entity/table first. If appropriate, add:

```
twoFactorEnabled BOOLEAN NOT NULL DEFAULT FALSE
totpSecret VARCHAR(...) NULL
```

Prefer a separate 2FA entity/table if the existing architecture supports multiple authentication factors.
Create the proper database migration using the project's existing migration system (Flyway/Liquibase).

## SECURITY

- Never store the TOTP secret as plaintext if the project already has an encryption mechanism.
- If no encryption mechanism exists, implement AES-GCM encryption for the TOTP secret using a server-side key from environment/configuration.
- Never log the TOTP secret, OTP codes, or backup codes — not even at DEBUG level.
- OTP must be exactly 6 digits; validate format before verification.
- Allow a time-window tolerance of ±1 step (30s) for clock drift. Do not widen this further.
- Reject reuse of an already-consumed TOTP code within the same time window (replay protection).
- Add rate limiting / brute-force protection for OTP verification if the project already has a rate-limiting mechanism.
- Do not expose the TOTP secret after 2FA setup is completed.
- Use HTTPS assumptions for production.
- Do not disable CSRF/security protections just to make the implementation work.

## TOTP LIBRARY

Use a well-maintained Java TOTP library compatible with the project's current Spring Boot/Java version.

Before adding a dependency:
- Check the existing `pom.xml` / `build.gradle`.
- Check Java and Spring Boot versions.
- Choose a compatible TOTP library — suggested: `dev.samstevens.totp:totp-spring-boot-starter` (Spring Boot 3 compatible) or `com.warrenstrange:googleauth`.
- Do not introduce unnecessary dependencies. Do NOT add Google API client libraries — they are irrelevant here.

## QR CODE

Prefer returning the `otpauth://` URI and letting the frontend render the QR code, if the existing frontend architecture supports it. Otherwise return a base64 PNG data URI.

## EXPECTED API DESIGN

```
POST /auth/2fa/setup
→ { "enabled": false, "otpauthUri": "otpauth://totp/...", "secret": "..." }

POST /auth/2fa/enable
← { "code": "123456" }
→ { "enabled": true, "backupCodes": ["...", "..."] }

POST /auth/2fa/verify
← { "temporaryToken": "...", "code": "123456" }
→ { "accessToken": "...", "refreshToken": "..." }

POST /auth/2fa/disable
← { "code": "123456" }
→ { "enabled": false }
```

Adapt endpoint names and response formats to the existing project's conventions instead of blindly copying these examples.

## ARCHITECTURE REQUIREMENTS

- Follow the project's existing package structure, DTO/request/response patterns, exception handling, validation patterns, and repository/service/controller patterns.
- Follow the existing JWT implementation.
- Do not duplicate authentication logic or create a second User authentication system.
- Do not modify unrelated features. Keep changes minimal and focused.

## TEMPORARY AUTHENTICATION

If the existing project uses JWT:
- Do not issue a normal access token before TOTP verification.
- Implement a short-lived temporary 2FA token (2–5 minutes max).
- It must carry a distinct claim/type (e.g. `typ: "2fa_pending"`) so security filters reject it on normal API endpoints.
- It should contain only the minimum information required to continue authentication.
- It must be single-use — invalidate it after verification succeeds or fails too many times.
- Do not simply return `userId` and trust the frontend to provide it back.

## BACKUP CODES

If the architecture allows it:
- Generate one-time backup codes when 2FA is enabled (e.g. 10 codes).
- Store only hashed backup codes (BCrypt/Argon2 — same hasher the project already uses for passwords).
- Each backup code can be used only once; mark as consumed atomically.
- Provide an endpoint to regenerate backup codes after proper authentication.
- Show plaintext codes exactly once, at generation time.

## TESTING

Add/update tests for:

1. Login with correct password + 2FA disabled
2. Login with incorrect password
3. Login with correct password + 2FA enabled
4. Correct TOTP code
5. Incorrect TOTP code
6. Expired temporary 2FA token
7. Reusing temporary 2FA token
8. Enabling 2FA with incorrect code
9. Enabling 2FA with correct code
10. Disabling 2FA
11. Unauthenticated attempt to disable 2FA
12. OTP brute-force / rate-limit behavior if supported
13. Backup code usage if implemented
14. Temporary 2FA token rejected on a normal protected endpoint

## MIGRATION SAFETY

- Create a proper database migration.
- Existing users must continue working with 2FA disabled by default.
- Do not make existing users unable to log in.
- Do not delete existing authentication columns/data.

## IMPLEMENTATION PROCESS

1. Inspect the project
2. Identify the current authentication flow
3. Identify User entity / database structure
4. Identify JWT/session implementation
5. Identify Spring Security configuration
6. Identify migration system
7. Propose the minimal implementation plan — **wait for my approval before coding**
8. Add the TOTP dependency
9. Implement database changes/migration
10. Implement TOTP service
11. Implement 2FA setup
12. Implement 2FA enable/verification
13. Integrate 2FA into existing login
14. Implement disable/recovery functionality
15. Add tests
16. Run the project's tests/build
17. Fix compilation/test failures
18. Review the final changes for security issues

## DO NOT

- Call any Google API, Google Identity, Google Cloud, or Firebase service
- Send OTP by SMS or email
- Rewrite the whole authentication system
- Replace Spring Security or the JWT implementation
- Hardcode secrets or encryption keys
- Store OTP codes
- Store TOTP secrets in plaintext
- Log secrets or OTP codes
- Automatically enable 2FA for existing users
- Issue a full JWT before successful 2FA verification
- Modify unrelated modules

## AT THE END, GIVE ME

1. Files changed
2. Database migration changes
3. New dependencies
4. API endpoints
5. Authentication flow
6. How to test with the Google Authenticator app manually
7. Environment variables / configuration that must be added
8. Any security concerns or remaining TODOs