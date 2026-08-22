Implement Google Authenticator 2FA (TOTP) in this existing Spring Boot application.

IMPORTANT:
- First inspect the existing project structure, authentication flow, Spring Security configuration, User entity, database schema, JWT/session implementation, login controller, services, and frontend/API flow.
- Do NOT rewrite or replace the existing authentication system.
- Reuse the existing architecture and coding patterns.
- Before modifying code, explain briefly which files/classes need to change and why.
- Keep the implementation production-ready and backward compatible.

GOAL:
Add Google Authenticator-based Two-Factor Authentication using TOTP.

AUTHENTICATION FLOW:

1. Normal login:
   POST /auth/login
   username + password

2. If password is invalid:
   - Return the existing authentication error.

3. If password is valid and 2FA is disabled:
   - Continue the existing login flow.
   - Return the normal JWT/session response.

4. If password is valid and 2FA is enabled:
   - DO NOT issue the final access token yet.
   - Return a temporary authentication result indicating that 2FA is required.
   - Use a secure temporary token/state instead of trusting a client-side boolean.

5. Frontend then submits:
   POST /auth/2fa/verify

   Example:
   {
     "temporaryToken": "...",
     "code": "123456"
   }

6. Verify the 6-digit TOTP code using the user's stored TOTP secret.

7. If valid:
   - Complete authentication.
   - Issue the normal JWT/access token and refresh token if the existing system uses them.

8. If invalid:
   - Return an appropriate authentication error.
   - Do not issue an access token.

2FA SETUP FLOW:

Create an authenticated endpoint such as:

POST /auth/2fa/setup

Behavior:
- Generate a cryptographically secure TOTP secret.
- Generate an otpauth:// URI compatible with Google Authenticator.
- Return the secret/QR-code data needed by the frontend.
- DO NOT enable 2FA yet.

Then create:

POST /auth/2fa/enable

Request:
{
  "code": "123456"
}

Behavior:
- Verify the TOTP code against the newly generated secret.
- Only after successful verification:
  - persist the encrypted TOTP secret
  - set twoFactorEnabled = true
- If verification fails, do not enable 2FA.

Also implement:

POST /auth/2fa/disable

Requirements:
- Require the user to be authenticated.
- Require appropriate verification before disabling 2FA.
- Never allow an unauthenticated request to disable 2FA.

DATABASE:

Inspect the existing User entity/table first.

If appropriate, add:

twoFactorEnabled BOOLEAN DEFAULT FALSE
totpSecret VARCHAR(...)

Prefer a separate 2FA entity/table if the existing architecture supports multiple authentication factors.

Create the proper database migration using the project's existing migration system.

SECURITY:

- Never store the TOTP secret as plaintext if the project already has an encryption mechanism.
- If no encryption mechanism exists, implement secure encryption for the TOTP secret using a server-side encryption key from environment/configuration.
- Never log the TOTP secret.
- Never log OTP codes.
- Never put the TOTP secret into normal application logs.
- OTP must be exactly 6 digits.
- Add rate limiting / brute-force protection for OTP verification if the project already has a rate-limiting mechanism.
- Do not expose the TOTP secret after 2FA setup is completed unless absolutely necessary.
- Use HTTPS assumptions for production.
- Do not disable CSRF/security protections just to make the implementation work.

TOTP LIBRARY:

Use a well-maintained Java TOTP library compatible with the project's current Spring Boot/Java version.

Before adding a dependency:
- Check the existing pom.xml/build.gradle.
- Check Java and Spring Boot versions.
- Choose a compatible TOTP library.
- Do not introduce unnecessary dependencies.

QR CODE:

The backend should provide either:
- an otpauth:// URI for the frontend to render as a QR code, OR
- a QR image/data format if that fits the existing architecture better.

Prefer returning the otpauth URI and letting the frontend render the QR code if the existing frontend architecture supports it.

EXPECTED API DESIGN:

POST /auth/2fa/setup

Response example:
{
  "enabled": false,
  "otpauthUri": "otpauth://totp/...",
  "secret": "..."
}

POST /auth/2fa/enable

Request:
{
  "code": "123456"
}

Response:
{
  "enabled": true
}

POST /auth/2fa/verify

Request:
{
  "temporaryToken": "...",
  "code": "123456"
}

Response:
{
  "accessToken": "...",
  "refreshToken": "..."
}

POST /auth/2fa/disable

Request:
{
  "code": "123456"
}

Response:
{
  "enabled": false
}

Adapt endpoint names and response formats to the existing project's conventions instead of blindly copying these examples.

IMPORTANT ARCHITECTURE REQUIREMENTS:

- Follow the project's existing package structure.
- Follow existing DTO/request/response patterns.
- Follow existing exception handling.
- Follow existing validation patterns.
- Follow existing repository/service/controller patterns.
- Follow existing JWT implementation.
- Do not duplicate authentication logic.
- Do not create a second User authentication system.
- Do not modify unrelated features.
- Keep changes minimal and focused.

TEMPORARY AUTHENTICATION:

If the existing project uses JWT:
- Do not issue a normal access token before TOTP verification.
- Implement a short-lived temporary 2FA token/state.
- The temporary token must not grant normal API access.
- It should contain only the minimum information required to continue authentication.
- It must expire quickly.
- Do not simply return userId and trust the frontend to provide it back.

BACKUP CODES:

If the architecture allows it, add recovery/backup codes.

Requirements:
- Generate one-time backup codes when 2FA is enabled.
- Store only hashed backup codes.
- Each backup code can be used only once.
- Provide an endpoint to regenerate backup codes after proper authentication.
- Never store backup codes in plaintext.

TESTING:

Add/update tests for:

1. Login with correct password + 2FA disabled.
2. Login with incorrect password.
3. Login with correct password + 2FA enabled.
4. Correct TOTP code.
5. Incorrect TOTP code.
6. Expired temporary 2FA token.
7. Reusing temporary 2FA token.
8. Enabling 2FA with incorrect code.
9. Enabling 2FA with correct code.
10. Disabling 2FA.
11. Unauthenticated attempt to disable 2FA.
12. OTP brute-force/rate-limit behavior if supported.
13. Backup code usage if implemented.

MIGRATION SAFETY:

- Create a proper database migration.
- Existing users must continue working with 2FA disabled by default.
- Do not make existing users unable to log in.
- Do not delete existing authentication columns/data.

IMPLEMENTATION PROCESS:

Follow this order:

1. Inspect the project.
2. Identify the current authentication flow.
3. Identify User/entity/database structure.
4. Identify JWT/session implementation.
5. Identify Spring Security configuration.
6. Identify migration system.
7. Propose the minimal implementation plan.
8. Implement the TOTP dependency.
9. Implement database changes/migration.
10. Implement TOTP service.
11. Implement 2FA setup.
12. Implement 2FA enable/verification.
13. Integrate 2FA into existing login.
14. Implement disable/recovery functionality.
15. Add tests.
16. Run the project's tests/build.
17. Fix compilation/test failures.
18. Review the final changes for security issues.

DO NOT:
- Rewrite the whole authentication system.
- Replace Spring Security.
- Replace JWT implementation.
- Hardcode secrets.
- Hardcode encryption keys.
- Store OTP codes.
- Store TOTP secrets in plaintext if secure encryption is available/required.
- Log secrets or OTP codes.
- Automatically enable 2FA for existing users.
- Issue a full JWT before successful 2FA verification.
- Modify unrelated modules.

At the end, give me:
1. Files changed
2. Database migration changes
3. New dependencies
4. API endpoints
5. Authentication flow
6. How to test Google Authenticator manually
7. Environment variables/configuration that must be added
8. Any security concerns or remaining TODOs