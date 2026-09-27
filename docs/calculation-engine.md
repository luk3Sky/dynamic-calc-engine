# Payroll Calculation Engine — Reference & Authoring Guide

This document is the complete guide to the payroll calculation engine: what it
can do, every configuration value it understands, how a calculation flows, and
— most importantly — **how to add new calculation rules correctly**, with
references that resolve and dependencies in the right order.

It is written for engineers who will operate the engine day to day and for the
future low-code UI that will author the same JSON files.

---

## Table of contents

1. [How a calculation runs](#1-how-a-calculation-runs)
2. [Configuration model overview](#2-configuration-model-overview)
3. [attributes.json — the attribute dictionary](#3-attributesjson--the-attribute-dictionary)
4. [eligibility-rules.json — Drools rules](#4-eligibility-rulesjson--drools-rules)
5. [formula-rules.json — jEasy formulas](#5-formula-rulesjson--jeasy-formulas)
6. [workflow.json — ordered stages](#6-workflowjson--ordered-stages)
7. [Supported calculation scenarios](#7-supported-calculation-scenarios)
8. [Adding new calculation rules](#8-adding-new-calculation-rules)
9. [Best practices & pitfalls](#9-best-practices--pitfalls)
10. [Validation & fail-fast behaviour](#10-validation--fail-fast-behaviour)
11. [API surface & request-to-attribute mapping](#11-api-surface--request-to-attribute-mapping)

---

## 1. How a calculation runs

A single request to `POST /api/v1/payroll/calculate` produces a `PayrollResult`
in one stateless pass through the two engines:

```
request → assemble CalculationContext → apply defaults
        → [workflow stages in order]
              ELIGIBILITY stage  → Drools (compiled once, cached KieBase; fresh KieSession per request)
              FORMULA stage      → jEasy (priority-ordered Rules per stage)
        → map final attributes → PayrollResult (itemized components + audit trace)
```

Three invariants drive everything:

1. **One fact bag.** Every rule reads and writes the same
   `CalculationContext` — a `Map<String,Object>` keyed by attribute id
   (`hourlyRate`, `grossTotal`, …) plus an audit `calculationTrace` and a
   `List<PayComponent>`.
2. **Config is the only source of truth.** There are no static `.drl` files and
   no hardcoded formulas. Drools DRL is *generated* from
   `eligibility-rules.json` at startup; jEasy rules are *built* from
   `formula-rules.json`. Every number in a result traces back to JSON.
3. **BigDecimal only.** All money is `BigDecimal`, never `double`/`float`.
   Formula results are rounded to 2 decimal places (`HALF_UP`) before being
   stored, so outputs are exact to the cent.

---

## 2. Configuration model overview

Four JSON files, loaded once at startup from `payroll.config.location`
(default `classpath:sample-config/`, any classpath directory or file-system
path), cross-validated, and cached:

| File | Purpose | Keyed by |
|------|---------|----------|
| `attributes.json` | Dictionary of every fact the engines may read/write, its type, origin and default | `attributes[].id` |
| `eligibility-rules.json` | Drools rules: which components apply, which slab | `rules[].ruleId` |
| `formula-rules.json` | jEasy rules: given a component applies, compute its value | `rules[].ruleId` |
| `workflow.json` | Ordered stages telling the orchestrator *which* engine runs *which* rules *when* | `stages[].name` |

Cross-references between files are validated at load time and **fail fast** —
an unknown `ruleId` in a workflow stage, an undeclared attribute reference, or
a malformed `BETWEEN`/`IN` value stops the application from starting (see
[§10](#10-validation--fail-fast-behaviour)).

> **Authoring mental model:** `attributes.json` is the *vocabulary*,
> `eligibility-rules.json` is *who qualifies*, `formula-rules.json` is *the
> maths*, and `workflow.json` is *the sequence*. A new calculation is a new word
> in the vocabulary, a new qualification, a new formula, and a slot in the
> sequence — in that order.

---

## 3. attributes.json — the attribute dictionary

```jsonc
{
  "attributes": [
    {
      "id": "hourlyRate",          // canonical name used everywhere (conditions, formulas, contexts)
      "displayName": "Hourly Rate",// human label (low-code UI)
      "dataType": "NUMBER",        // see table below
      "source": "EMPLOYEE_MASTER", // see table below
      "defaultValue": 0            // applied when a request omits the attribute
    }
  ]
}
```

### 3.1 Supported `dataType`

| Value     | Java type  | Notes                                                        |
|-----------|------------|--------------------------------------------------------------|
| `NUMBER`  | `BigDecimal` | All numeric comparisons/arithmetic go through `BigDecimal`. JSON decimals keep their declared scale (`0.10` stays `0.10`). |
| `STRING`  | `String`   | Compared with `==`/`equals` in MVEL, `.equals()` in Drools.  |
| `BOOLEAN` | `Boolean`  | Eligibility flags and conditions.                            |

### 3.2 Supported `source`

| Value              | Meaning                                             |
|--------------------|-----------------------------------------------------|
| `EMPLOYEE_MASTER`  | Supplied by the request `employee` block.            |
| `ATTENDANCE`       | Supplied by the request `attendance` block.          |
| `ACTIVITY`         | Supplied by the request `activities[]` block.        |
| `COMPUTED`         | Written by rules during the calculation.             |

`source` is descriptive — nothing in the engine branches on it — but keeping it
accurate keeps the dictionary honest for the low-code UI.

### 3.3 `defaultValue` semantics

The orchestrator pre-seeds every declared attribute that a request did not
supply with its `defaultValue` (see `ContextDefaults`). Consequences:

- **A `null` default means "leave absent"** — e.g. `taxSlab` and `activityType`.
  Do not reference such attributes from a formula that may run before they are
  produced (see the MVEL rule in [§5.3](#53-mvel-authoring-rules)).
- **Every computed output that other formulas reference should default to `0`**
  (all the sample outputs do). "Component does not apply" is modelled as
  *"stays at its zero default"*, which is exactly why formulas can be plain
  sums without null-guards.
- Defaults are numbers/strings/booleans converted per `dataType` — a `NUMBER`
  default becomes `BigDecimal`.

### 3.4 The complete dictionary shipped with the engine

**Request inputs**

| id | dataType | source | default |
|----|----------|--------|---------|
| `hourlyRate` | NUMBER | EMPLOYEE_MASTER | `0` |
| `standardHours` | NUMBER | ATTENDANCE | `0` |
| `overtimeHours` | NUMBER | ATTENDANCE | `0` |
| `overtimeMultiplier` | NUMBER | EMPLOYEE_MASTER | `1.5` |
| `employeeGrade` | NUMBER | EMPLOYEE_MASTER | `1` |
| `employmentType` | STRING | EMPLOYEE_MASTER | `"FULL_TIME"` |
| `activityType` | STRING | ACTIVITY | `null` |
| `activityUnits` | NUMBER | ACTIVITY | `0` |
| `activityUnitRate` | NUMBER | ACTIVITY | `0` |
| `transportAllowance` | NUMBER | EMPLOYEE_MASTER | `0` |
| `mealAllowance` | NUMBER | EMPLOYEE_MASTER | `0` |
| `providentFundRate` | NUMBER | EMPLOYEE_MASTER | `0.12` |

**Computed flags and classification**

| id | dataType | source | default |
|----|----------|--------|---------|
| `overtimeEligible` | BOOLEAN | COMPUTED | `false` |
| `activityBonusEligible` | BOOLEAN | COMPUTED | `false` |
| `taxSlab` | STRING | COMPUTED | `null` |
| `incomeTaxRate` | NUMBER | COMPUTED | `0` |

**Computed outputs** (all `NUMBER`, `COMPUTED`, default `0`)

`basePay`, `overtimePay`, `activityPay`, `transportAllowanceTotal`,
`mealAllowanceTotal`, `totalAllowances`, `grossTotal`, `incomeTax`,
`providentFund`, `totalDeductions`, `netPay`.

---

## 4. eligibility-rules.json — Drools rules

Each rule is compiled into generated DRL at startup (no static `.drl`). It
fires when **all** its conditions match, then writes its derived facts into the
context and appends a trace line.

```jsonc
{
  "rules": [
    {
      "ruleId": "RULE_OVERTIME_ELIGIBLE",
      "description": "Full-time employees at grade 4 or above qualify for overtime premium pay.",
      "conditions": [
        { "attribute": "employmentType", "operator": "EQUALS", "value": "FULL_TIME" },
        { "attribute": "employeeGrade",  "operator": "GREATER_THAN", "value": 3 }
      ],
      "derivedFacts": { "overtimeEligible": true }
    }
  ]
}
```

### 4.1 Supported operators

| Operator       | `value` shape                    | Semantics (against the context attribute)              |
|----------------|----------------------------------|--------------------------------------------------------|
| `EQUALS`       | scalar                           | Equal. Numerics compare by value (0.10 == 0.1).         |
| `NOT_EQUALS`   | scalar                           | Not equal. **Missing attribute also matches** (null ≠ value). |
| `IN`           | array                            | Attribute value present in the array.                  |
| `GREATER_THAN` | scalar                           | Numeric comparison (both sides → `BigDecimal`).        |
| `LESS_THAN`    | scalar                           | Numeric comparison.                                    |
| `BETWEEN`      | exactly two elements `[low, high]` | Inclusive on both ends: `low ≤ value ≤ high`.          |

Conditions are **AND-ed**. There is no OR — add another rule instead (Drools
rules are independent and additive).

### 4.2 Missing-attribute behaviour (important)

Eligibility evaluation is *null-safe*: a missing attribute means **"no data ⇒
not eligible"**, not an error.

| Operator       | Missing attribute result |
|----------------|--------------------------|
| `EQUALS` / `IN` / `GREATER_THAN` / `LESS_THAN` / `BETWEEN` | `false` (rule does not fire) |
| `NOT_EQUALS`   | `true` (null ≠ value) — the rule fires |

This is what lets slab rules reference `grossTotal` harmlessly before it
exists; it also means **`NOT_EQUALS` is dangerous as an applicability guard** —
prefer `EQUALS` on a positive value or an explicit eligibility flag.

### 4.3 `derivedFacts` value coercion

| JSON value      | Written into the context as |
|-----------------|-----------------------------|
| number (e.g. `0.10`) | `BigDecimal("0.10")` — scale preserved |
| string (e.g. `"SLAB_1"`) | `String` |
| boolean (e.g. `true`) | `Boolean` |

### 4.4 Rules shipped with the engine

| ruleId | Conditions | Derived facts |
|--------|------------|---------------|
| `RULE_OVERTIME_ELIGIBLE` | `employmentType EQUALS FULL_TIME`, `employeeGrade GREATER_THAN 3` | `overtimeEligible = true` |
| `RULE_ACTIVITY_BONUS_ELIGIBLE` | `activityType IN [DELIVERY, SALES_UNIT, PIECEWORK]`, `activityUnits GREATER_THAN 0` | `activityBonusEligible = true` |
| `RULE_TAX_SLAB_LOW` | `grossTotal BETWEEN [0, 1200]` | `taxSlab = SLAB_1`, `incomeTaxRate = 0` |
| `RULE_TAX_SLAB_MID` | `grossTotal BETWEEN [1200.01, 4000]` | `taxSlab = SLAB_2`, `incomeTaxRate = 0.10` |
| `RULE_TAX_SLAB_HIGH` | `grossTotal GREATER_THAN 4000` | `taxSlab = SLAB_3`, `incomeTaxRate = 0.20` |

Slab boundaries are inclusive: gross `1200.00` → `SLAB_1`; `1200.01` →
`SLAB_2`; `4000.00` → `SLAB_2`; `4000.01` → `SLAB_3`.

---

## 5. formula-rules.json — jEasy formulas

Each rule is a `condition → formula` step executed in `priority` order inside
its workflow stage.

```jsonc
{
  "rules": [
    {
      "ruleId": "RULE_OVERTIME_PAY",
      "name": "Overtime Pay",                  // PayComponent display name
      "description": "Premium pay at the configured multiplier for eligible overtime hours.",
      "priority": 2,                           // execution order within its stage
      "condition": "overtimeEligible == true && overtimeHours > 0",  // MVEL boolean
      "formula": "hourlyRate * overtimeHours * overtimeMultiplier",  // MVEL arithmetic
      "outputAttribute": "overtimePay",        // result is written here
      "componentType": "EARNING"               // EARNING | DEDUCTION, omitted for aggregates
    }
  ]
}
```

When a rule fires it: evaluates the formula → rounds to 2dp `HALF_UP` →
writes `outputAttribute` → appends a readable trace line → records a
`PayComponent` **only when `componentType` is present**.

### 5.1 `componentType`

| Value        | Effect                                              |
|--------------|-----------------------------------------------------|
| `EARNING`    | Recorded as an itemized earning line (`grossPay` side). |
| `DEDUCTION`  | Recorded as an itemized deduction line.             |
| *(omitted)*  | Aggregates and intermediate values (`totalAllowances`, `grossTotal`, `totalDeductions`, `netPay`) — **not** surfaced as line items. |

### 5.2 `priority`

- Lower priority runs first **within a stage**. Rules in one stage that depend
  on each other must have increasing priorities in data-flow order (see
  `ALLOWANCES` and `STATUTORY_DEDUCTIONS`).
- Priorities are only meaningful *within* a stage; they do not order stages
  (stage order comes from `workflow.json`).

### 5.3 MVEL authoring rules

Conditions and formulas are MVEL expressions evaluated against the attribute
map (`context.getAttributes()`). These are hard constraints of the MVEL 2.5.x
runtime used here:

1. **Every referenced attribute must already exist in the map.**
   MVEL throws `PropertyAccessException: unresolvable property or identifier`
   on a missing key. This is why computed outputs default to `0` — an
   inapplicable component is present as `0`, not absent.
2. **`?:` (elvis) only works on booleans.** `x ?: 0` fails with
   `expected Boolean; but found …` for numeric `x`. Do not use elvis to
   default values. Use one of:
   - rely on the `0` default (preferred), or
   - an explicit ternary `x != null ? x : 0` (still requires `x` to *exist*).
3. **String literals**: single or double quotes both work —
   `employmentType == 'FULL_TIME'`.
4. **Boolean comparison**: `overtimeEligible == true`, or just
   `overtimeEligible`.
5. **Comparisons across numeric types** (`BigDecimal > 0`, `BigDecimal * 1.5`)
   work; MVEL coerces via `BigDecimal`.
6. **A formula must return a number.** Non-numeric or `null` results throw
   `FormulaEvaluationException` with `ruleId` and `outputAttribute`.
7. **Keep constants out of formulas.** A bare `1.5` literal in a formula is
   coerced to `double`, contaminating the `BigDecimal` discipline. Store rates
   as attributes instead (the sample uses `overtimeMultiplier: 1.5`).

### 5.4 Audit trace

Each fired rule appends one line built by substituting attribute values into
the formula, e.g.:

```
RULE_OVERTIME_PAY: hourlyRate(25.00) * overtimeHours(4) * overtimeMultiplier(1.5) = 150.00
RULE_INCOME_TAX: grossTotal(1375.00) * incomeTaxRate(0.10) = 137.50
```

The trace is the explainability contract of the engine: every number in it
resolves to an attribute with its actual value.

### 5.5 Formulas shipped with the engine

| ruleId | condition | formula | output | componentType |
|--------|-----------|---------|--------|---------------|
| `RULE_BASE_PAY` | `standardHours > 0` | `hourlyRate * standardHours` | `basePay` | EARNING |
| `RULE_OVERTIME_PAY` | `overtimeEligible == true && overtimeHours > 0` | `hourlyRate * overtimeHours * overtimeMultiplier` | `overtimePay` | EARNING |
| `RULE_ACTIVITY_PAY` | `activityBonusEligible == true` | `activityUnits * activityUnitRate` | `activityPay` | EARNING |
| `RULE_TRANSPORT_ALLOWANCE` | `transportAllowance > 0` | `transportAllowance` | `transportAllowanceTotal` | EARNING |
| `RULE_MEAL_ALLOWANCE` | `mealAllowance > 0` | `mealAllowance` | `mealAllowanceTotal` | EARNING |
| `RULE_TOTAL_ALLOWANCES` | `true` | `transportAllowanceTotal + mealAllowanceTotal` | `totalAllowances` | — |
| `RULE_GROSS_TOTAL` | `true` | `basePay + overtimePay + activityPay + totalAllowances` | `grossTotal` | — |
| `RULE_INCOME_TAX` | `incomeTaxRate > 0` | `grossTotal * incomeTaxRate` | `incomeTax` | DEDUCTION |
| `RULE_PROVIDENT_FUND` | `employmentType == 'FULL_TIME'` | `basePay * providentFundRate` | `providentFund` | DEDUCTION |
| `RULE_TOTAL_DEDUCTIONS` | `true` | `incomeTax + providentFund` | `totalDeductions` | — |
| `RULE_NET_PAY` | `true` | `grossTotal - totalDeductions` | `netPay` | — |

---

## 6. workflow.json — ordered stages

The workflow is the *sequence*. Each stage names the engine that executes it
and the exact rules it runs.

```jsonc
{
  "stages": [
    {
      "name": "INITIAL_ELIGIBILITY",
      "engine": "ELIGIBILITY",        // ELIGIBILITY (Drools) | FORMULA (jEasy)
      "ruleIds": ["RULE_OVERTIME_ELIGIBLE", "RULE_ACTIVITY_BONUS_ELIGIBLE"],
      "execution": "SEQUENTIAL"       // SEQUENTIAL | PARALLEL
    }
  ]
}
```

### 6.1 Supported values

| Field       | Values | Meaning |
|-------------|--------|---------|
| `engine`    | `ELIGIBILITY` | Drools fires exactly `ruleIds` via an agenda filter; they must exist in `eligibility-rules.json`. |
|             | `FORMULA` | jEasy runs exactly `ruleIds`; they must exist in `formula-rules.json`. |
| `execution` | `SEQUENTIAL` | Rules run in `priority` order; each rule's outputs are visible to the next in the stage. |
|             | `PARALLEL` | No ordering contract; rules are independent. (easy-rules 4.x removed rule groups, so both modes execute in priority order — `PARALLEL` documents intent only.) |

### 6.2 Dependency ordering across stages

Stages run top-to-bottom. An attribute is **readable only after the stage that
produces it**. The shipped workflow is a dependency chain:

```
INITIAL_ELIGIBILITY   (Drools: overtime + activity flags)   — needs only request inputs
BASE_PAY              (formula)                              — needs hourlyRate, standardHours
OVERTIME              (formula)                              — needs basePay inputs + overtimeEligible
ACTIVITY_PAY          (formula)                              — needs activity* + activityBonusEligible
ALLOWANCES            (formula)                              — needs allowance inputs
GROSS_TOTAL           (formula)                              — needs all earnings + allowances
TAX_SLAB_DETERMINATION(Drools: classifies from grossTotal)   — needs grossTotal  ← second pass
STATUTORY_DEDUCTIONS  (formula: incomeTax, PF, total)        — needs taxSlab/incomeTaxRate, basePay
NET_PAY               (formula)                              — needs grossTotal, totalDeductions
```

### 6.3 The two-pass pattern (tax-slab circular dependency)

Tax slab classification depends on `grossTotal`, which only exists after the
earnings stages. The engine resolves this **in config** by running two Drools
passes, with `GROSS_TOTAL` sandwiched between them:

```text
… → GROSS_TOTAL → TAX_SLAB_DETERMINATION → STATUTORY_DEDUCTIONS → NET_PAY
```

`TAX_SLAB_DETERMINATION` is an `ELIGIBILITY` stage placed after
`GROSS_TOTAL`; the deduction formulas then consume `incomeTaxRate`. This
pattern generalises to *any* classification-that-depends-on-a-computed-value:
compute the value (formula stage) → classify it (eligibility stage) → consume
the classification (formula stage).

---

## 7. Supported calculation scenarios

The engine composes freely from its components. A component is included when
its formula's condition is true; otherwise its output stays at its `0` default
and it produces **no** `PayComponent`.

### 7.1 The three reference scenarios

| Scenario | Inputs (summary) | Earnings | Slab / deductions | gross | net |
|----------|------------------|----------|-------------------|-------|-----|
| **Full-time + overtime** (EMP-1001) | FULL_TIME, grade 4, 25.00/h × 40 h + 4 OT h, transport 150, meal 75, PF 12% | base 1000.00, overtime 150.00, transport 150.00, meal 75.00 | SLAB_2 (10%) → tax 137.50; PF 120.00 | 1375.00 | 1117.50 |
| **Part-time, no overtime** (EMP-2002) | PART_TIME, grade 2, 20.00/h × 20 h, no activity, no allowances | base 400.00 | SLAB_1 (0%) → tax 0; no PF (PART_TIME) | 400.00 | 400.00 |
| **Activity contractor** (EMP-3003) | CONTRACT, PIECEWORK 500 @ 2.50 | activity 1250.00 | SLAB_2 (10%) → tax 125.00; no PF (CONTRACT) | 1250.00 | 1125.00 |

### 7.2 Behaviour matrix for any combination

| Employee profile | What pays | What is skipped |
|------------------|-----------|-----------------|
| FULL_TIME, grade ≥ 4, overtime hours > 0 | base, overtime, allowances, PF, tax | activity pay (unless eligible activity units) |
| FULL_TIME, grade ≤ 3, overtime hours > 0 | base, allowances, PF, tax | **overtime** (grade threshold) |
| FULL_TIME, grade ≥ 4, overtime hours = 0 | base, allowances, PF, tax | overtime (no hours) |
| PART_TIME, any activity with units | base, activity pay, tax | overtime (never FULL_TIME), PF (never FULL_TIME) |
| CONTRACT with eligible activity units | activity pay, tax | base (no standard hours), overtime, PF |
| CONTRACT with no activity | nothing → gross 0.00, net 0.00 | everything (valid, if unusual) |
| Any profile, zero allowances | base/overtime/activity as applicable | allowance line items (allowance `> 0` gate) |
| Any gross ≤ 1200.00 | — | income tax (SLAB_1 rate 0) |
| Any gross > 4000.00 | tax at 20% | — (higher slab) |

### 7.3 Edge behaviour to know

- **Slab boundaries are exact** (`BigDecimal`): gross 1200.00 → SLAB_1,
  1200.01 → SLAB_2, 4000.00 → SLAB_2, 4000.01 → SLAB_3.
- **Zero-amount components are not line items**: allowance rules gate on
  `allowance > 0`, and `INCOME_TAX` gates on `incomeTaxRate > 0`, so a 0.00
  line item never appears.
- **Inapplicable components leave a `0` trace footprint**: GROSS_TOTAL's trace
  line shows `overtimePay(0)` etc., making the trace auditable even when a
  component did not apply.
- **`employmentType` values**: `FULL_TIME | PART_TIME | CONTRACT` (drives
  overtime and PF). **`activityType`** is free-form; the sample eligibility
  whitelist is `DELIVERY | SALES_UNIT | PIECEWORK` — add new types to that
  `IN` list (and to the whitelist's `attributes.json` default if desired).

---

## 8. Adding new calculation rules

This is the core authoring workflow. Follow it top-to-bottom; each step's
inputs must already exist when the next step references them.

### 8.1 The five-step recipe

**Step 1 — declare the vocabulary** (`attributes.json`)
Add every new fact. Three rules of thumb:
- A **computed output** that other formulas will reference → `dataType: NUMBER`,
  `source: COMPUTED`, `defaultValue: 0`.
- Any **rate/constant** the formula needs → its own attribute (e.g.
  `overtimeMultiplier`) so no `double` literal sneaks into MVEL.
- Keep ids unique, camelCase, and identical to the identifiers used in
  conditions/formulas.

**Step 2 — (optional) define applicability** (`eligibility-rules.json`)
If a component should only apply under conditions (grade, employment type,
thresholds), add an eligibility rule that derives a boolean flag. Conditions
may only reference attributes that exist by the time the rule runs.

**Step 3 — add the maths** (`formula-rules.json`)
Add the formula rule. Reference only:
- request inputs (Step-1 attributes), and/or
- flags from Step 2, and/or
- outputs of rules that run **before** it (see 8.2).
Use `priority` to order within a stage; set `componentType` to `EARNING` or
`DEDUCTION` for line items, omit for aggregates.

**Step 4 — wire the sequence** (`workflow.json`)
Add the rule's `ruleId` to the correct stage:
- flag-producing eligibility rule → the `INITIAL_ELIGIBILITY` stage, or a
  *post-`GROSS_TOTAL`* eligibility stage if it classifies a computed value;
- the formula rule → a stage positioned **after** all of its inputs and
  **before** everything that consumes its output.
Alternatively add the rule to an existing stage whose input set already
satisfies Step 3.

**Step 5 — update consumers**
If any existing aggregate must include the new value, edit its formula
(e.g. add `nightShiftAllowance` to `RULE_GROSS_TOTAL`) and/or its stage's
`ruleIds`. This step is easy to forget — the engine cannot "know" a new earning
belongs in `grossTotal` until you say so.

Then start the app: `ConfigLoaderService` validates every cross-reference and
fails fast with an actionable message if anything is wrong.

### 8.2 Dependency-ordering rules (the invariants)

1. **Reference only declared attributes.** Every identifier in a condition or
   formula must exist in `attributes.json` (validation enforces this — see
   §10).
2. **An attribute's value exists only after the rule that produces it.** Within
   a stage that means higher `priority`; across stages that means a later
   stage.
3. **Never reference an un-produced output.** E.g. a formula in `BASE_PAY`
   cannot read `grossTotal`; `TAX_SLAB_DETERMINATION` must come after
   `GROSS_TOTAL`.
4. **Classification before consumption.** If a deduction depends on a
   classification that depends on a computed value, use the two-pass pattern
   (§6.3): compute → classify → consume.
5. **Aggregates must name every part.** `RULE_GROSS_TOTAL` sums every earning
   explicitly; adding an earning means editing that formula.
6. **New computed outputs default to `0`.** Otherwise every formula that reads
   them throws MVEL's unresolved-identifier error.
7. **`componentType` only on line items.** Aggregates (`grossTotal`, `netPay`,
   `totalDeductions`, `totalAllowances`) must omit it or they will appear as
   fake line items.
8. **One rule per stage position.** A ruleId may appear in more than one stage,
   but a sensible workflow keeps each rule in exactly one stage for clarity.

### 8.3 Worked example A — night-shift allowance (EARNING)

Goal: full-time employees with `nightShiftHours > 0` earn `10%` of base pay.

**Step 1 — `attributes.json`:**
```json
{ "id": "nightShiftHours", "displayName": "Night Shift Hours", "dataType": "NUMBER", "source": "ATTENDANCE", "defaultValue": 0 },
{ "id": "nightShiftRate",  "displayName": "Night Shift Rate",  "dataType": "NUMBER", "source": "EMPLOYEE_MASTER", "defaultValue": 0.10 },
{ "id": "nightShiftEligible", "displayName": "Night Shift Eligible", "dataType": "BOOLEAN", "source": "COMPUTED", "defaultValue": false },
{ "id": "nightShiftAllowance", "displayName": "Night Shift Allowance", "dataType": "NUMBER", "source": "COMPUTED", "defaultValue": 0 }
```

**Step 2 — `eligibility-rules.json`:**
```json
{
  "ruleId": "RULE_NIGHT_SHIFT_ELIGIBLE",
  "description": "Full-time employees with recorded night shift hours qualify.",
  "conditions": [
    { "attribute": "employmentType", "operator": "EQUALS", "value": "FULL_TIME" },
    { "attribute": "nightShiftHours", "operator": "GREATER_THAN", "value": 0 }
  ],
  "derivedFacts": { "nightShiftEligible": true }
}
```

**Step 3 — `formula-rules.json`:** (add to the `ALLOWANCES` stage, priority 1 so
it runs before `RULE_TOTAL_ALLOWANCES`)
```json
{
  "ruleId": "RULE_NIGHT_SHIFT_ALLOWANCE",
  "name": "Night Shift Allowance",
  "priority": 1,
  "condition": "nightShiftEligible == true",
  "formula": "basePay * nightShiftRate",
  "outputAttribute": "nightShiftAllowance",
  "componentType": "EARNING"
}
```
`basePay` exists by then (`BASE_PAY` stage ran earlier); `nightShiftRate`
defaults to `0.10`; the output defaults to `0`.

**Step 4 — `workflow.json`:**
- add `RULE_NIGHT_SHIFT_ELIGIBLE` to `INITIAL_ELIGIBILITY.ruleIds`;
- add `RULE_NIGHT_SHIFT_ALLOWANCE` to `ALLOWANCES.ruleIds`.

**Step 5 — consumers:** `RULE_TOTAL_ALLOWANCES` and `RULE_GROSS_TOTAL` must
include the new value:
```jsonc
"formula": "transportAllowanceTotal + mealAllowanceTotal + nightShiftAllowance",   // TOTAL_ALLOWANCES
"formula": "basePay + overtimePay + activityPay + totalAllowances + nightShiftAllowance" // GROSS_TOTAL
```
(Or give night-shift its own stage before `GROSS_TOTAL` and add it only to
`RULE_GROSS_TOTAL`.)

### 8.4 Worked example B — professional tax (DEDUCTION, classification-dependent)

Goal: a flat `200.00` professional tax when `grossTotal > 2000`.

This mirrors the tax-slab shape, so it uses the **two-pass pattern**.

**Step 1 — `attributes.json`:**
```json
{ "id": "professionalTaxAmount", "displayName": "Professional Tax Amount", "dataType": "NUMBER", "source": "EMPLOYEE_MASTER", "defaultValue": 200 },
{ "id": "professionalTaxApplicable", "displayName": "Professional Tax Applicable", "dataType": "BOOLEAN", "source": "COMPUTED", "defaultValue": false },
{ "id": "professionalTax", "displayName": "Professional Tax", "dataType": "NUMBER", "source": "COMPUTED", "defaultValue": 0 }
```

**Step 2 — `eligibility-rules.json`:** the classification *depends on
`grossTotal`*, so it must run in the post-`GROSS_TOTAL` eligibility pass:
```json
{
  "ruleId": "RULE_PROFESSIONAL_TAX_APPLICABLE",
  "description": "Employees with gross pay above 2000.00 pay professional tax.",
  "conditions": [ { "attribute": "grossTotal", "operator": "GREATER_THAN", "value": 2000 } ],
  "derivedFacts": { "professionalTaxApplicable": true }
}
```

**Step 3 — `formula-rules.json`:** (add to `STATUTORY_DEDUCTIONS`, before
`RULE_TOTAL_DEDUCTIONS`)
```json
{
  "ruleId": "RULE_PROFESSIONAL_TAX",
  "name": "Professional Tax",
  "priority": 1,
  "condition": "professionalTaxApplicable == true",
  "formula": "professionalTaxAmount",
  "outputAttribute": "professionalTax",
  "componentType": "DEDUCTION"
}
```

**Step 4 — `workflow.json`:** add `RULE_PROFESSIONAL_TAX_APPLICABLE` to
`TAX_SLAB_DETERMINATION.ruleIds` (the post-gross eligibility stage) and
`RULE_PROFESSIONAL_TAX` to `STATUTORY_DEDUCTIONS.ruleIds`.

**Step 5 — consumers:** `RULE_TOTAL_DEDUCTIONS` becomes
`incomeTax + providentFund + professionalTax`; `NET_PAY` is unchanged (it
already subtracts `totalDeductions`).

---

## 9. Best practices & pitfalls

### Do

- **Store rates and thresholds as attributes**, not MVEL literals
  (`overtimeMultiplier`, `professionalTaxAmount`). This preserves `BigDecimal`
  purity and keeps every tunable in config.
- **Default every computed output to `0`** so formulas are plain sums.
- **Gate line items on a positive input or an eligibility flag** so zero-amount
  components never surface as line items.
- **Order within a stage by `priority` in data-flow order**, and order stages
  so each stage's inputs are complete before it starts.
- **Give every rule a unique `ruleId`** and add it to exactly one stage.
- **Use the two-pass pattern** whenever a classification depends on a computed
  value (slabs, thresholds over `grossTotal`, …).
- **Keep the audit trace in mind**: prefer formulas whose identifiers are
  self-explanatory — the trace is your debugging and compliance output.
- **Test at slab boundaries** (1200.00 / 1200.01 / 4000.00 / 4000.01) and with
  absent components (0 hours, 0 units, no activities).

### Don't

- **Don't use `double`/`float`** — add a `NUMBER` attribute instead of a raw
  literal.
- **Don't use elvis `?:` for value defaults** in MVEL (boolean-only) — rely on
  `0` defaults.
- **Don't reference an attribute that may not exist yet** (MVEL throws; the
  engine fails the request, not the rule).
- **Don't use `NOT_EQUALS` as an applicability guard** (it matches when the
  attribute is missing).
- **Don't add a component without updating its aggregates** — `grossTotal` /
  `totalDeductions` will silently omit it.
- **Don't put `componentType` on aggregates** — they'd appear as bogus line
  items.
- **Don't edit config while the app is running** — config is loaded once at
  startup (MVP1); restart after changes.

---

## 10. Validation & fail-fast behaviour

`ConfigLoaderService` (`PayrollConfigValidator`) checks the whole bundle at
startup and refuses to start on any inconsistency. The messages name the
offender so you can fix config — not Java:

| Check | Example message (abridged) |
|-------|----------------------------|
| Duplicate ids | `duplicate attribute id [hourlyRate]` / `duplicate formula ruleId [RULE_X]` |
| Eligibility condition references unknown attribute | `eligibility rule [RULE_X] condition #1 references attribute [foo] which is not declared in attributes.json` |
| Derived fact references unknown attribute | `eligibility rule [RULE_X] derives attribute [foo] …` |
| `BETWEEN` value not a 2-element array | `eligibility rule [RULE_X] condition #1 uses BETWEEN but value is not a two-element array` |
| `IN` value not an array | `eligibility rule [RULE_X] condition #1 uses IN but value is not an array` |
| Workflow references unknown ruleId | `workflow stage [GROSS_TOTAL] references formula ruleId [RULE_DOES_NOT_EXIST] which does not exist in formula-rules.json` |
| Formula output not declared | `formula rule [RULE_X] outputs attribute [foo] which is not declared in attributes.json` |
| Formula references unknown attribute | `formula rule [RULE_X] expression references attribute [foo] …` (string literals and MVEL keywords are ignored) |

Config location resolution: `classpath:dir/` or a file-system path; a missing
file or unparseable JSON also fails fast at startup.

---

## 11. API surface & request-to-attribute mapping

**Endpoints**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/payroll/calculate` | Runs the pipeline and returns `PayrollResult`. |
| GET | `/api/v1/payroll/config/attributes` | Attribute dictionary (read-only, low-code UI). |
| GET | `/api/v1/payroll/config/rules` | Loaded eligibility + formula rules (read-only). |

**Request → attribute mapping** (assembled by `PayrollCalculationAssembler`)

| Request field | Context attribute |
|---------------|-------------------|
| `employeeId` | `context.employeeId` |
| `period.startDate / endDate` | `context.period` |
| `employee.grade` | `employeeGrade` |
| `employee.employmentType` | `employmentType` (enum name string) |
| `employee.hourlyRate` | `hourlyRate` |
| `employee.providentFundRate` | `providentFundRate` |
| `employee.transportAllowance` | `transportAllowance` |
| `employee.mealAllowance` | `mealAllowance` |
| `employee.overtimeMultiplier` | `overtimeMultiplier` |
| `attendance.standardHours` | `standardHours` |
| `attendance.overtimeHours` | `overtimeHours` |
| `activities[0].activityType` | `activityType` |
| `activities[0].units` | `activityUnits` |
| `activities[0].unitRate` | `activityUnitRate` |

> MVP1 uses **only the first activity record**; per-activity aggregation is on
> the roadmap. Omitted fields fall back to `attributes.json` defaults.

**Responses**

- `200` — `PayrollResult`: `employeeId`, `period`, itemized `components`,
  `grossPay` / `totalDeductions` / `netPay` (BigDecimal), `calculationTrace`.
- `400` — Bean Validation failure or malformed body (structured `ApiError`,
  no stack traces).
- `422` — rule-evaluation failure (`EligibilityEvaluationException` /
  `FormulaEvaluationException`, carrying `ruleId` / `outputAttribute`).
- `500` — unexpected error (logged server-side only).

---

## Appendix — engine notes

- **Drools `9.44.0.Final`** (`kie-ci`, `drools-core`, `drools-mvel`,
  `drools-compiler`). DRL is generated per eligibility rule; the `KieBase`
  compiles **once** at startup and is cached; only a `KieSession` is created
  per request (see the code comment in `DroolsEligibilityEngine` for the
  per-request-vs-pooling justification). Each stage fires through a
  `RuleNameMatchesAgendaFilter` restricted to its `ruleIds`.
- **jEasy `4.1.0`** (`org.jeasy:easy-rules-core/mvel`). The brief's "jEasy 6.x"
  does not exist on Maven Central or GitHub; 4.1.0 is the latest published
  version. Rule groups were removed in 4.x, so `SEQUENTIAL` stages are ordered
  by rule `priority` inside a stage's priority-sorted `Rules` set.
- **MVEL 2.5.x** constraints are summarised in [§5.3](#53-mvel-authoring-rules).
- **JSON numbers** are parsed with exact-BigDecimal node factories so declared
  scale (`0.10`) is preserved end-to-end.