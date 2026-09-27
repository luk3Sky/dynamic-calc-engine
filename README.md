# payroll-calc-engine

A Gradle-based **modular-monolith** Spring Boot 3.x application that calculates
employee pay — from hourly + activity-based components through to final net
compensation — using **two rule engines working together**:

- **Drools (KIE)** handles *eligibility* and *classification* rules (which pay
  components apply, which tax slab, which thresholds).
- **jEasy** handles *calculation/formula* execution (given a component applies,
  compute its value).

Everything — attributes, eligibility rules, formulas, and workflow ordering —
is sourced from external **JSON config files**, loaded and compiled at runtime.
There are no static `.drl` files and no hardcoded business logic in Java.

This is **MVP1**: JSON-driven only, no database, no persistence, a stateless
calculation API, built in anticipation of a future **low-code UI** that will
edit these same JSON files.

---

## Documentation

- **[docs/calculation-engine.md](docs/calculation-engine.md)** — the full
  engine reference: how a calculation flows, every supported config value, all
  supported scenarios, and a step-by-step guide to **adding new calculation
  rules** with correct references and dependency ordering.
- **[docs/sample-requests.http](docs/sample-requests.http)** — ready-to-run
  requests for all three sample employees and the config introspection
  endpoints.

---

## Modules

| Module         | Responsibility                                                          |
|----------------|-------------------------------------------------------------------------|
| `common`       | Domain models, JSON config schema, `ConfigLoaderService` (load + validate) |
| `rules-engine` | Drools eligibility engine, jEasy calculation engine, orchestrator       |
| `api`          | Spring Boot REST layer (`/api/v1/payroll/**`), validation, error handling |

Dependency direction: `api` → `rules-engine` → `common`.

---

## Quickstart

Requirements: JDK 21.

```bash
./gradlew build          # compiles all three modules
./gradlew test           # runs common + rules-engine + api tests (exact-value pay assertions)
./gradlew bootRun        # starts the API on http://localhost:8080
```

Swagger UI: <http://localhost:8080/swagger-ui.html> ·
OpenAPI JSON: <http://localhost:8080/v3/api-docs>

The engine config lives under `common/src/main/resources/sample-config/` and
can be pointed at another location with
`--payroll.config.location=classpath:sample-config/` (or a file-system path).

---

## API

| Method | Path                                  | Description                                      |
|--------|---------------------------------------|--------------------------------------------------|
| POST   | `/api/v1/payroll/calculate`           | Run the full Drools + jEasy pipeline              |
| GET    | `/api/v1/payroll/config/attributes`   | Attribute dictionary (read-only, for low-code UI) |
| GET    | `/api/v1/payroll/config/rules`        | Loaded eligibility + formula rules (read-only)    |

`200` returns a fully itemized `PayrollResult` (BigDecimal `grossPay` /
`totalDeductions` / `netPay`, components, audit trace). `400` = validation,
`422` = rule-evaluation failure, `500` = unexpected error — all as structured
`ApiError` bodies with no stack traces leaked.

> Note: the brief originally asked for "jEasy 6.x"; no such release exists on
> Maven Central or GitHub, so the latest published **4.1.0**
> (`org.jeasy:easy-rules-core/mvel`) is used.