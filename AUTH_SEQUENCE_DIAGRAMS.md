# Two-Factor Auth Flows

Sequence diagrams for every request/response path through the JWT + TOTP
two-factor authentication API, traced call-by-call from `controller` through
`service.impl` to `repository`. Generated from the current sources under
`src/main/java/com/example/spring_boot_project_api`.

**Key settings** (`application.properties`):

| Setting | Value |
|---|---|
| Access token TTL | 15m |
| Refresh token TTL | 7d |
| 2FA-pending token TTL | 5m |
| Lockout | 5 failures / 15m window → 15m lock |
| Backup codes | 10, single-use |
| Secret at rest | AES-256-GCM |

## Contents

1. [Register](#1-register)
2. [Login](#2-login)
3. [Verify 2FA code](#3-verify-2fa-code)
4. [Enable 2FA](#4-enable-2fa)
5. [Authenticated request](#5-authenticated-request)
6. [Refresh token](#6-refresh-token)
7. [Disable / regenerate 2FA](#7-disable--regenerate-2fa)

---

## 1. Register

`POST /auth/register` — creates an account with 2FA off by default. No tokens
are issued here; the client logs in separately afterward.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant AC as AuthController
    participant AS as AuthServiceImpl
    participant UR as UserRepository
    participant PE as PasswordEncoder

    C->>AC: POST /auth/register { username, password }
    AC->>AS: register(request)
    AS->>UR: existsByUsername(username)
    UR-->>AS: boolean

    alt username already taken
        AS-->>AC: throw DuplicateUsernameException
        AC-->>C: 409 Conflict
    else username free
        AS->>PE: encode(password)
        PE-->>AS: bcryptHash
        AS->>UR: save(User{twoFactorEnabled=false})
        UR-->>AS: User
        AS-->>AC: UserResponse
        AC-->>C: 201 Created
    end
```

**Rules encoded above**

- The username-uniqueness check runs before any password hashing — a duplicate never touches `PasswordEncoder`.
- The plaintext password is never persisted; only the BCrypt hash reaches `UserRepository.save`.
- Every new row starts with `twoFactorEnabled=false`; 2FA is opt-in via a separate setup flow.

---

## 2. Login

`POST /auth/login` — password check only. The response shape depends on
whether the account has 2FA enabled: either full tokens, or a short-lived
token that grants nothing but the verify step.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant AC as AuthController
    participant AS as AuthServiceImpl
    participant UR as UserRepository
    participant PE as PasswordEncoder
    participant JWT as JwtServiceImpl

    C->>AC: POST /auth/login { username, password }
    AC->>AS: login(request)
    AS->>UR: findByUsername(username)
    UR-->>AS: Optional<User>
    AS->>PE: matches(password, user.passwordHash)
    PE-->>AS: boolean

    alt user missing or password wrong
        note over AS,PE: burns one bcrypt round against a dummy hash so an<br/>unknown username and a wrong password cost the same time
        AS->>PE: matches("timing-equalization", DUMMY_PASSWORD_HASH)
        AS-->>AC: throw InvalidCredentialsException
        AC-->>C: 401 Unauthorized
    else password valid, twoFactorEnabled = true
        AS->>JWT: generateTwoFactorPendingToken(user)
        note right of JWT: typ=TWO_FACTOR_PENDING, ttl=5m<br/>only /auth/2fa/verify accepts it
        JWT-->>AS: temporaryToken
        AS-->>AC: LoginResponse{twoFactorRequired=true}
        AC-->>C: 200 OK { temporaryToken }
    else password valid, twoFactorEnabled = false
        AS->>JWT: issueTokens(user)
        JWT-->>AS: accessToken, refreshToken
        AS-->>AC: LoginResponse{twoFactorRequired=false}
        AC-->>C: 200 OK { accessToken, refreshToken }
    end
```

**Rules encoded above**

- Unknown username and wrong password return the identical exception and cost the identical time, so timing can't be used to enumerate accounts.
- A 2FA-enabled account never gets a real access/refresh pair from `/login` — only a 5-minute pending token scoped to one endpoint.
- Accounts without 2FA skip the pending-token round trip entirely and walk out with full tokens on this one call.

---

## 3. Verify 2FA code

`POST /auth/2fa/verify` (public) — completes login for a 2FA account: trades
a pending token plus a 6-digit code (TOTP or backup code) for real tokens.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant TC as TwoFactorAuthController
    participant T2FA as TwoFactorAuthServiceImpl
    participant JWT as JwtServiceImpl
    participant OTP as OtpAttemptService
    participant UR as UserRepository
    participant TAR as TwoFactorAuthRepository
    participant CR as CryptoService
    participant TOTP as TotpService
    participant BCR as BackupCodeRepository
    participant PE as PasswordEncoder

    C->>TC: POST /auth/2fa/verify { temporaryToken, code }
    TC->>T2FA: verify(temporaryToken, code)
    T2FA->>JWT: parseAndValidate(token, TWO_FACTOR_PENDING)
    JWT-->>T2FA: claims { subject, jti, expiresAt }
    T2FA->>OTP: isPendingTokenConsumed(jti)
    OTP-->>T2FA: boolean
    alt jti already consumed
        T2FA-->>TC: throw InvalidTokenException
        TC-->>C: 401 — token already used
    end
    T2FA->>UR: findByUsername(claims.subject)
    UR-->>T2FA: User
    alt twoFactorEnabled turned off since login
        T2FA-->>TC: throw InvalidTokenException
        TC-->>C: 401 — pending token can't complete login
    end
    T2FA->>OTP: assertAttemptsAllowed(user.id)
    alt 5 failures already recorded in the last 15m
        OTP-->>T2FA: throw TwoFactorLockedException
        T2FA-->>TC: propagates
        TC-->>C: locked — retry after N seconds
    end
    T2FA->>TAR: findById(user.id)
    TAR-->>T2FA: TwoFactorAuth{secretEnc}
    T2FA->>CR: decrypt(secretEnc)
    CR-->>T2FA: secret
    T2FA->>TOTP: verify(secret, code)
    alt TOTP code valid
        note right of TOTP: current 30s window ± 1 period drift
    else TOTP invalid — fall back to backup codes
        T2FA->>BCR: findByUserIdAndUsedAtIsNull(user.id)
        BCR-->>T2FA: unused codes
        loop each unused code
            T2FA->>PE: matches(code, codeHash)
        end
        alt a backup code matched
            T2FA->>BCR: save(code{usedAt=now})
            note right of BCR: single-use — burned immediately
        else nothing matched
            T2FA->>OTP: recordFailure(user.id)
            T2FA-->>TC: throw InvalidTwoFactorCodeException
            TC-->>C: 401 Unauthorized
        end
    end
    T2FA->>OTP: resetFailures(user.id)
    T2FA->>OTP: markPendingTokenConsumed(jti, expiresAt)
    T2FA->>JWT: issueTokens(user)
    JWT-->>T2FA: accessToken, refreshToken
    T2FA-->>TC: AuthResponse
    TC-->>C: 200 OK { accessToken, refreshToken }
```

**Rules encoded above**

- `/auth/2fa/verify` is the one route in the whole API that accepts a `TWO_FACTOR_PENDING` token — every other endpoint rejects it (see [Authenticated request](#5-authenticated-request)).
- Each pending token completes login at most once: its `jti` is recorded as consumed the instant it succeeds.
- Failures are rate-limited per user — 5 in a rolling 15-minute window locks further attempts for 15 minutes.
- A TOTP mismatch isn't a hard fail: the same code is also checked against unused backup codes before the request is rejected.

---

## 4. Enable 2FA

`POST /auth/2fa/setup` → `POST /auth/2fa/enable` (authenticated) — two calls,
same session: stage a secret and get a QR code, then prove you can generate
a code from it before 2FA actually turns on.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant TC as TwoFactorAuthController
    participant T2FA as TwoFactorAuthServiceImpl
    participant UR as UserRepository
    participant TAR as TwoFactorAuthRepository
    participant TOTP as TotpService
    participant CR as CryptoService
    participant BCR as BackupCodeRepository
    participant PE as PasswordEncoder

    note over C,TC: Step 1 — stage a secret (Authorization: Bearer access token)
    C->>TC: POST /auth/2fa/setup
    TC->>T2FA: setup(principal.username)
    T2FA->>UR: findByUsername(username)
    UR-->>T2FA: User
    alt 2FA already enabled
        T2FA-->>TC: throw TwoFactorAlreadyEnabledException
        TC-->>C: 409 Conflict
    end
    T2FA->>TOTP: generateSecret()
    TOTP-->>T2FA: secret
    T2FA->>CR: encrypt(secret)
    CR-->>T2FA: pendingSecretEnc
    T2FA->>TAR: save(TwoFactorAuth{pendingSecretEnc})
    note right of TAR: staged only — 2FA is still disabled
    T2FA->>TOTP: buildOtpauthUri(secret, username)
    TOTP-->>T2FA: otpauth://totp/...
    T2FA-->>TC: TwoFactorSetupResponse{secret, otpauthUri}
    TC-->>C: 200 OK — client renders it as a QR code

    note over C: user scans the QR in an authenticator app,<br/>then types back the 6-digit code it shows

    note over C,TC: Step 2 — confirm the code, enable 2FA
    C->>TC: POST /auth/2fa/enable { code }
    TC->>T2FA: enable(username, code)
    T2FA->>TAR: findById(user.id)
    TAR-->>T2FA: TwoFactorAuth{pendingSecretEnc}
    T2FA->>CR: decrypt(pendingSecretEnc)
    CR-->>T2FA: stagedSecret
    T2FA->>TOTP: verify(stagedSecret, code)
    alt code invalid
        T2FA-->>TC: throw InvalidTwoFactorCodeException
        TC-->>C: 401 — staged secret is kept, client may retry
    else code valid
        T2FA->>TAR: secretEnc = pendingSecretEnc; pendingSecretEnc = null
        T2FA->>UR: user.twoFactorEnabled = true
        T2FA->>BCR: deleteByUserId(user.id)
        loop 10x (app.two-factor.backup-code-count)
            T2FA->>PE: encode(plainBackupCode)
        end
        T2FA->>BCR: saveAll(hashedCodes)
        T2FA-->>TC: TwoFactorEnableResponse{backupCodes}
        TC-->>C: 200 OK — show the 10 codes once, never again
    end
```

**Rules encoded above**

- The secret is AES-256-GCM encrypted before it's ever written to `TwoFactorAuth.pendingSecretEnc` — nothing sensitive touches disk in the clear.
- 2FA only flips to `true` after the client echoes back a valid code — scanning the QR alone never enables it.
- A wrong confirmation code leaves the staged secret untouched, so the client can retry `/enable` without re-running `/setup`.
- Backup codes are generated and returned exactly once, at the moment 2FA turns on, and stored only as BCrypt hashes.

---

## 5. Authenticated request

`JwtAuthenticationFilter` — runs once per request, ahead of Spring
Security's own authentication filter, for anything not explicitly permitted
in `SecurityConfig`.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant FLT as JwtAuthenticationFilter
    participant JWT as JwtServiceImpl
    participant SC as SecurityContextHolder
    participant CTRL as Controller
    participant EH as RestSecurityProblemHandler

    C->>FLT: request, header Authorization: Bearer <token>
    FLT->>JWT: parseAndValidate(token, ACCESS)

    alt missing / malformed / wrong token type / bad signature / expired
        JWT-->>FLT: throw InvalidTokenException
        FLT->>SC: clearContext()
        note right of SC: stale auth from a pooled thread can't leak into this request
        FLT->>CTRL: chain continues, unauthenticated
        CTRL->>EH: request reaches an @authenticated route with no principal
        EH-->>C: 401 Unauthorized (problem+json)
    else token valid
        JWT-->>FLT: claims { subject, uid, jti, expiresAt }
        FLT->>SC: setAuthentication(subject, ROLE_USER)
        FLT->>CTRL: chain continues, authenticated
        CTRL->>CTRL: principal.getName() == claims.subject
        CTRL-->>C: 200 OK
    end
```

**Rules encoded above**

- Only `TokenType.ACCESS` passes here — a refresh token or a 2FA-pending token in the header is rejected exactly like a garbage string.
- `/auth/register`, `/auth/login`, `/auth/refresh`, and `/auth/2fa/verify` are the only POST routes that skip this check (permitted in `SecurityConfig`); everything else requires a valid access token.
- The filter is deliberately not a `@Component` — it's wired only into the security filter chain so it can't accidentally run twice and overwrite the authentication it just set.

---

## 6. Refresh token

`POST /auth/refresh` — trades a still-valid refresh token for a brand new
access/refresh pair, without re-checking the password.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant AC as AuthController
    participant AS as AuthServiceImpl
    participant JWT as JwtServiceImpl
    participant UR as UserRepository

    C->>AC: POST /auth/refresh { refreshToken }
    AC->>AS: refresh(request)
    AS->>JWT: parseAndValidate(refreshToken, REFRESH)

    alt invalid, wrong type, or expired (ttl 7d)
        JWT-->>AS: throw InvalidTokenException
        AS-->>AC: propagates
        AC-->>C: 401 Unauthorized
    else valid
        JWT-->>AS: claims { subject }
        AS->>UR: findByUsername(claims.subject)
        alt user no longer exists
            UR-->>AS: empty
            AS-->>AC: throw InvalidCredentialsException
            AC-->>C: 401 Unauthorized
        else user found
            UR-->>AS: User
            AS->>JWT: issueTokens(user)
            note right of JWT: mints a fresh pair — the old refresh<br/>token isn't revoked, just superseded
            JWT-->>AS: accessToken, refreshToken
            AS-->>AC: AuthResponse
            AC-->>C: 200 OK { accessToken, refreshToken }
        end
    end
```

**Rules encoded above**

- Only a `REFRESH`-typed token is accepted; an access token in this slot fails the same way an expired one would.
- There's no server-side denylist — refreshing doesn't revoke the token just used, so a leaked refresh token stays valid until its own 7-day expiry.
- If the user row was deleted after the token was issued, refresh fails closed with `InvalidCredentialsException` rather than trusting the token's claims alone.

---

## 7. Disable / regenerate 2FA

`POST /auth/2fa/disable` · `POST /auth/2fa/backup-codes` (authenticated) —
both endpoints share the same guard: a valid TOTP or backup code is required
before anything about the account's 2FA state changes.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant TC as TwoFactorAuthController
    participant T2FA as TwoFactorAuthServiceImpl
    participant UR as UserRepository
    participant TAR as TwoFactorAuthRepository
    participant CR as CryptoService
    participant TOTP as TotpService
    participant BCR as BackupCodeRepository
    participant PE as PasswordEncoder
    participant OTP as OtpAttemptService

    C->>TC: POST /auth/2fa/disable  or  /auth/2fa/backup-codes { code }
    TC->>T2FA: disable(username, code)  or  regenerateBackupCodes(username, code)
    T2FA->>UR: findByUsername(username)
    UR-->>T2FA: User
    alt 2FA not enabled
        T2FA-->>TC: throw TwoFactorNotEnabledException
        TC-->>C: 409 Conflict
    end
    T2FA->>TAR: findById(user.id)
    TAR-->>T2FA: TwoFactorAuth{secretEnc}
    T2FA->>CR: decrypt(secretEnc)
    CR-->>T2FA: secret
    T2FA->>TOTP: verify(secret, code)
    alt TOTP invalid
        T2FA->>BCR: findByUserIdAndUsedAtIsNull(user.id)
        BCR-->>T2FA: unused codes
        loop each unused code
            T2FA->>PE: matches(code, codeHash)
        end
    end

    alt code (TOTP or backup) invalid
        T2FA-->>TC: throw InvalidTwoFactorCodeException
        TC-->>C: 401 Unauthorized
    else code valid, request was /disable
        T2FA->>UR: user.twoFactorEnabled = false
        T2FA->>TAR: secretEnc = null, pendingSecretEnc = null
        T2FA->>BCR: deleteByUserId(user.id)
        T2FA->>OTP: resetFailures(user.id)
        T2FA-->>TC: TwoFactorStatusResponse{enabled=false}
        TC-->>C: 200 OK
    else code valid, request was /backup-codes
        T2FA->>BCR: deleteByUserId(user.id)
        loop 10x (app.two-factor.backup-code-count)
            T2FA->>PE: encode(plainBackupCode)
        end
        T2FA->>BCR: saveAll(hashedCodes)
        T2FA-->>TC: BackupCodesResponse{backupCodes}
        TC-->>C: 200 OK — new codes shown once, old codes now invalid
    end
```

**Rules encoded above**

- Neither endpoint trusts the session alone — both demand a fresh TOTP or backup code before touching account state.
- Disabling wipes `secretEnc`, `pendingSecretEnc`, and every backup code in one transaction; re-enabling later means starting [Enable 2FA](#4-enable-2fa) from zero.
- Regenerating backup codes invalidates every previously issued code, including ones that were never used.
