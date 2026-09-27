# payroll-calc-engine

A Gradle-based modular-monolith Spring Boot 3.x application that calculates
employee pay (hourly + activity-based components through to final net
compensation) using two rule engines working together:

- **Drools** (KIE) handles *eligibility* and *classification* rules.
- **jEasy** handles *calculation/formula* execution.

Everything — attributes, eligibility rules, formulas, and workflow ordering —
is sourced from external JSON config files that are loaded and compiled at
runtime. There is no static `.drl`, no database, and no hardcoded business
logic in Java.

This is **MVP1**: JSON-driven only, stateless calculation API, built in
anticipation of a future low-code UI that will edit these same JSON files.

## Modules

| Module        | Responsibility                                              |
|---------------|-------------------------------------------------------------|
| `common`      | Domain models, JSON config schema, `ConfigLoaderService`    |
| `rules-engine`| Drools eligibility engine, jEasy calculation engine, orchestrator |
| `api`         | Spring Boot REST layer (`/api/v1/payroll/**`)               |

## Build

```bash
./gradlew build
./gradlew test
./gradlew bootRun
```

Swagger UI is available at `http://localhost:8080/swagger-ui.html`.

> Documentation (architecture, JSON schema reference, API usage) is expanded
> in the final documentation step.