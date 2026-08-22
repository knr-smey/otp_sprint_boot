# Spring Boot Project API

A Spring Boot REST API built with Java 21, Spring Data JPA, MySQL, and springdoc-openapi
(Swagger UI).

## Tech stack

- Java 21
- Spring Boot 4.0.8-SNAPSHOT
- Spring Data JPA + MySQL
- Bean Validation (jakarta.validation)
- springdoc-openapi (Swagger UI)
- Lombok

## Getting started

Clone the repository, then detach it from this repo's git history so you can start your own:

```bash
git clone <repo-url> spring_boot_project_api
cd spring_boot_project_api
rm -rf .git
git init
```

### Prerequisites

- JDK 21
- A local MySQL instance — either Docker or XAMPP (see below)

### Configure the database

`application.properties` already points at `localhost:3306`, database `spring_boot_project_api`,
user `root` with no password — the default for both options below, so no edits are needed to get
running. Everyone on the team gets an identical local DB without sharing credentials.

**Option A — Docker (recommended):**

```bash
docker compose up -d
```

This starts MySQL 8 on port `3306` (database `spring_boot_project_api` auto-created) and
phpMyAdmin at `http://localhost:8081` for browsing the DB. Data persists in a named volume
across restarts; run `docker compose down -v` to wipe it.

**Option B — XAMPP:**

1. Install XAMPP and start the MySQL module from the XAMPP control panel (default port `3306`).
2. Open phpMyAdmin (`http://localhost/phpmyadmin`) and create a database named
   `spring_boot_project_api` (or let the app create it — `createDatabaseIfNotExist=true` is
   already set).

**Custom credentials / port conflicts:** if your setup differs (e.g. port 3306 is already taken
by another project's container), copy `.env.example` to `.env` and adjust it:

```bash
cp .env.example .env
# edit .env, e.g. DB_PORT=3307
```

`docker compose` picks up `.env` automatically. For the Spring Boot app to match, export the same
variables before running it (or set them in your IDE run config):

```bash
DB_PORT=3307 ./mvnw spring-boot:run
```

`.env` is git-ignored — it's for personal machine-specific overrides, not shared config.

### Run

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`, and Swagger UI at
`http://localhost:8080/swagger-ui.html`.

### Test

```bash
./mvnw test
```

## Project structure

See [`agent_guide_ai.md`](agent_guide_ai.md) for the full folder layout and coding conventions
(controller → service → repository layering, DTOs, mappers, etc.). That file is also the shared
source of truth read by AI coding agents (Claude Code, Codex, opencode, Copilot, Antigravity).
