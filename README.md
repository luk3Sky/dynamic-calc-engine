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

The app also ships an **admin console** (server-rendered Thymeleaf, no frontend
build, no database) for viewing and editing those same JSON config files through
structured forms — see [Admin Console](#admin-console).

This is **MVP1**: JSON-driven only, no database, no persistence beyond the JSON
files (plus a single `.bak` safety net), a stateless calculation API.

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
| `common`       | Domain models, JSON config schema, `ConfigLoaderService` (load + validate), `ConfigValidator` |
| `rules-engine` | `ConfigRuntime` (shared live config holder), Drools eligibility engine, jEasy calculation engine, orchestrator |
| `admin-ui`     | Server-rendered Thymeleaf admin console (`/admin`) for editing the four config files |
| `api`          | Spring Boot REST layer (`/api/v1/payroll/**`), validation, error handling, embeds `admin-ui` |

Dependency direction: `api` → `admin-ui` → `rules-engine` → `common`.

---

## Quickstart

Requirements: JDK 21.

```bash
./gradlew build          # compiles all four modules
./gradlew test           # runs common + rules-engine + api tests (exact-value pay assertions)
./gradlew bootRun        # starts the API on http://localhost:8080
```

A single `bootRun` from the `api` module starts both the REST API and the admin
console — `admin-ui` is a dependency of `api`, not a standalone deployable, so
they share one Spring context and one JVM.

- REST API: <http://localhost:8080/api/v1/payroll/calculate>
- Swagger UI: <http://localhost:8080/swagger-ui.html> ·
  OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Admin console: <http://localhost:8080/admin>

The engine config lives under `common/src/main/resources/sample-config/` and
can be pointed at another location with
`--payroll.config.location=classpath:sample-config/` (or a **file-system path**).

> Note: the admin console's *Save to File* action requires a filesystem config
> location, e.g.
> `./gradlew bootRun --args='--payroll.config.location=/etc/payroll/config'`.
> With the default `classpath:` location Apply to Runtime works, but Save to
> File is disabled with a clear explanation.

---

## Admin Console

The console at **`/admin`** lets an operator edit the four JSON config files
through structured, server-rendered forms — **Attributes**, **Eligibility
Rules**, **Formula Rules** and **Workflow** — plus a Dashboard.

### How it shares state with the API

There is exactly **one** live config in the JVM: the `ConfigRuntime` bean in
`rules-engine` holds an atomic, thread-safe snapshot of the validated config
plus the *compiled* Drools `KieBase` and the prebuilt jEasy rule sets. Both the
calculation orchestrator (and therefore `POST /api/v1/payroll/calculate`) and
every admin editor read from and write to this **same** instance. Editing a rule
through the console and applying it changes what the very next calculation
request does — no restart.

### Apply to Runtime vs Save to File — and why both exist

| Action            | What it does                                                                 |
|-------------------|------------------------------------------------------------------------------|
| **Validate**      | Runs the exact `ConfigValidator` used at startup against the pending edit; renders field-level errors inline. Changes nothing. |
| **Apply to Runtime** | Re-validates, then calls `ConfigRuntime.reload(...)`, which recompiles the Drools KieBase and rebuilds the jEasy rule sets and atomically swaps them in. **Memory only — nothing is written to disk.** |
| **Save to File**  | Re-validates, applies to runtime, then pretty-prints the config back to its original JSON files, copying each previous file to a single rolling `.bak` first. Memory and disk can never disagree. |
| **Reload from Disk** | Re-reads the four JSON files through `ConfigLoaderService` and re-activates them, discarding any unsaved in-memory edits (confirmed by the browser before submitting). |

The two are deliberately separate because they fail in different ways and an
operator should control when each happens:

- **Apply to Runtime is cheap, reversible and safe to experiment with.** A bad
  edit is caught by validation and never applied at all; a merely "wrong"
  (but valid) edit can be undone with *Reload from Disk*, and nothing on disk
  is touched. This is the loop for iterating on rules live.
- **Save to File is the committed, durable step.** Once you are happy with the
  live behaviour you persist it so a restart keeps the change. The `.bak` is a
  one-step safety net — there is deliberately **no version history and no
  rollback UI** in this MVP.

**Validation hard-blocks both Apply and Save**: an invalid config can never be
activated at runtime *or* written to disk. `ConfigRuntime.reload` itself
re-runs the validator, so even a bypassed controller cannot load an invalid
config.

The Dashboard shows the loaded counts, whether the in-memory state differs from
what is on disk, and the last-saved time per config file.

### Editing notes

- Dropdowns for attributes / rules / ruleIds in dependent editors always
  reflect the **current in-memory** state, so editing several config types in
  one session stays consistent (apply attributes, then the eligibility dropdown
  already shows the new attribute).
- The formula editor includes a **Test Expression** action that evaluates the
  MVEL condition/formula against a sample context built from current attribute
  defaults, showing the result or a specific MVEL error inline.
- The workflow editor reorders stages with simple up/down controls — no
  drag-and-drop library.

### Roadmap: authentication / authorization

**The admin console is intentionally unauthenticated in this MVP.** Any process
that can reach the app on port 8080 can edit live payroll configuration and
apply it to the running engine. **Authentication and authorization must be
added before any non-local (staging/production) deployment** — for example
Spring Security with role-based access (operator vs read-only) over `/admin/**`,
plus audit logging of Validate / Apply / Save / Reload actions.

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