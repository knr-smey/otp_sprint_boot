# AI Agent Guide

This is the canonical project guide for AI coding agents (Claude Code, OpenAI Codex, opencode,
GitHub Copilot, Google Antigravity, etc.). Tool-specific config files (`CLAUDE.md`, `AGENTS.md`,
`.github/copilot-instructions.md`) all point back here — this file is the single source of truth.
Update this file when conventions change; keep the pointer files short.

## Project overview

Spring Boot REST API.

- **Language / runtime:** Java 21
- **Framework:** Spring Boot 4.0.8-SNAPSHOT (spring-boot-starter-parent)
- **Persistence:** Spring Data JPA + MySQL (`mysql-connector-j`)
- **Validation:** spring-boot-starter-validation (Jakarta Bean Validation)
- **API docs:** springdoc-openapi-starter-webmvc-ui (Swagger UI)
- **Boilerplate:** Lombok
- **Build tool:** Maven (use the `./mvnw` wrapper, not a system-wide `mvn`)
- **Base package:** `com.example.spring_boot_project_api`

## Authentication: email OTP login

Every login is two-step; there is no password-only session.

1. `POST /auth/register` — create account (`{username, email, password}`) → 201 + user JSON.
2. `POST /auth/login` — `{username, password}` → 200 with
   `{otpRequired, temporaryToken, maskedEmail, expiresInSeconds}` and a 6-digit code mailed out.
3. `POST /auth/otp/verify` — `{temporaryToken, code}` → `{accessToken, refreshToken}`.
4. `POST /auth/otp/send` — `{temporaryToken}` → resend code (60s cooldown enforced).

Key pieces:

- **Tokens:** HS256 JWTs from `JwtService` with a `typ` claim (`ACCESS`, `REFRESH`, `OTP_PENDING`).
  The pending token only grants `/auth/otp/*`, is single-use (consumed jti), and expires in 5m.
- **Brute force:** `OtpAttemptService` counts failed verifications per user (5 failures / 15m window
  → 15m lockout, HTTP 429 via `OtpLockedException`).
- **Codes:** stored BCrypt-hashed in `user_email_otp` (one row per user, deleted on success);
  mail sent by `SmtpOtpMailSender` (Gmail app password via `GMAIL_USERNAME`/`GMAIL_APP_PASSWORD`).
- **Tunables:** `app.jwt.*` and `app.otp.*` in `application.properties` bind to
  `config/JwtProperties` and `config/OtpProperties`.
- **Security:** stateless filter chain in `config/SecurityConfig`; everything except
  `/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**` requires a valid ACCESS token.
  Security errors return RFC 7807-style problem bodies via `RestSecurityProblemHandler`.

## Build, run, test

```bash
./mvnw clean compile        # compile
./mvnw spring-boot:run       # run the app locally
./mvnw test                  # run tests
./mvnw clean package         # build the jar
```

Swagger UI is available at `/swagger-ui.html` once the app is running.

## Folder structure

```
src/main/java/com/example/spring_boot_project_api/
├── SpringBootProjectApiApplication.java   # main entry point
├── config/           # @Configuration classes (OpenAPI, CORS, beans, etc.)
├── controller/        # @RestController — HTTP layer only, delegates to service
├── dto/
│   ├── request/       # inbound request payloads (validated with jakarta.validation)
│   └── response/       # outbound response payloads
├── model/             # @Entity JPA persistence models
├── exception/          # custom exceptions + @RestControllerAdvice global handler
├── mapper/            # model <-> DTO conversion
├── repository/          # Spring Data JPA repositories
├── service/
│   ├── *.java          # service interfaces
│   └── impl/          # service implementations
└── util/              # stateless helpers/constants

src/test/java/com/example/spring_boot_project_api/
├── controller/         # @WebMvcTest slice tests
├── repository/         # @DataJpaTest slice tests
└── service/            # unit tests (Mockito)
```

## Conventions

- **Layering:** `controller` → `service` → `repository`. Controllers never touch entities or
  repositories directly; they work with DTOs and call service interfaces.
- **DTOs:** never expose JPA `model` classes directly over the API — always map to a
  `dto/request` or `dto/response` type via the `mapper` package.
- **Services:** define an interface in `service/`, implementation in `service/impl/`.
- **Errors:** throw specific exceptions from `exception/`, handled centrally by a
  `@RestControllerAdvice` — don't catch-and-swallow in controllers.
- **Validation:** use `jakarta.validation` annotations on request DTOs; let Spring's validation
  handle rejection rather than manual null-checks in controllers.
- **Lombok:** prefer `@Getter/@Setter`/`@Builder`/`@RequiredArgsConstructor` over hand-written
  boilerplate; use constructor injection (via `@RequiredArgsConstructor`) instead of `@Autowired`
  field injection.
- **Config:** database connection and environment-specific settings belong in
  `application.properties` (or profile-specific `application-{profile}.properties`), not
  hardcoded in Java.

## Notes for agents

- Don't add a new architectural layer or dependency unless the task actually needs it.
- Keep controller methods thin; business logic belongs in the service layer.
- When adding a new resource (e.g. `Product`), create matching files across `model`,
  `repository`, `dto/request`, `dto/response`, `mapper`, `service` (+ `impl`), and `controller` —
  don't skip the DTO/mapper layer "just this once."
- Run `./mvnw test` before considering a change complete.
