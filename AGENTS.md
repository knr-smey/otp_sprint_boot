# Agent Instructions

The full project guide for AI agents lives in [`agent_guide_ai.md`](./agent_guide_ai.md) at the
repo root — **read that file before making changes.** It covers the tech stack, folder layout,
build/test commands, and coding conventions for this Spring Boot API.

Quick reference:

```bash
./mvnw spring-boot:run   # run the app
./mvnw test                # run tests
```

Base package: `com.example.spring_boot_project_api` — layered as
`controller` → `service` → `repository`, with `model`/`dto`/`mapper` kept separate. See
`agent_guide_ai.md` for the full breakdown and conventions.
