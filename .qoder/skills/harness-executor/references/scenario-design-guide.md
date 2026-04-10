# Task-Aware Scenario Design Guide

This guide explains how the Coordinator designs functional verification scenarios for each task. Unlike pre-defined scenarios in `environment.json`, these are **dynamically generated** based on what the task actually changed.

## Why Design Scenarios Per-Task

Pre-defined scenarios in `environment.json` cover common flows:
- User registration/login
- Health checks
- Standard CRUD operations

But they **cannot** cover:
- The specific endpoint you just added
- The validation rule you just implemented
- The edge case your change introduced
- The side effects of your modification

**Task-specific scenarios bridge this gap.**

---

## The Design Process

### Step 1: Gather Context

Before designing scenarios, the Coordinator collects:

| Source | What to Extract |
|--------|-----------------|
| Task description | What feature/fix was requested |
| Plan file (Goal section) | Expected outcomes |
| Subagent results | `files_changed`, `files_created`, `summary` |
| environment.json | Available databases, services, scripts |
| Code inspection | Routes, handlers, validation logic |

### Step 2: Identify What Changed

Categorize the changes:

| Change Type | Example | What to Verify |
|-------------|---------|----------------|
| New endpoint | `POST /api/v1/products` | Returns correct status, creates data, validates input |
| Modified endpoint | Updated `/api/v1/users/{id}` | New fields work, old behavior unchanged |
| New validation | Email format check | Rejects invalid, accepts valid |
| New business rule | "Orders must have at least 1 item" | Rule enforced, error message clear |
| Side effects | "Send email on registration" | Side effect occurs (or mock called) |
| Auth changes | Added admin-only endpoint | Rejects non-admin, accepts admin |

### Step 3: Design Scenarios

For each significant change, create 1-3 scenarios covering:

1. **Happy path**: The feature works as intended
2. **Error path**: Invalid input is rejected correctly
3. **Edge cases**: Boundary conditions, concurrent access, etc.

---

## Scenario Template

```json
{
  "name": "verify_<feature>_<behavior>",
  "description": "One sentence describing what this verifies",
  "requires": ["<dependency from environment.json>"],
  "steps_hint": [
    "<step 1>",
    "<step 2>",
    "<step 3>"
  ],
  "why": "<business reason this matters>",
  "priority": "high | medium | low"
}
```

### Field Descriptions

| Field | Purpose | Example |
|-------|---------|---------|
| `name` | Unique identifier, snake_case | `verify_product_price_validation` |
| `description` | What success looks like | "Verify product creation rejects negative prices" |
| `requires` | Dependencies from environment.json | `["postgres", "redis"]` or `[]` |
| `steps_hint` | Natural language steps (verifier fills in details) | See examples below |
| `why` | Business justification | "Price integrity is critical for billing" |
| `priority` | Execution priority | `high` = must pass, `medium` = should pass |

---

## Examples by Change Type

### New Endpoint (CRUD)

**Task**: "Add product management API"

```json
[
  {
    "name": "verify_create_product_success",
    "description": "Verify POST /api/products creates product in database",
    "requires": ["postgres"],
    "steps_hint": [
      "POST /api/products with valid data (name, price, category)",
      "Assert 201 response with product ID",
      "GET /api/products/{id} to verify persistence",
      "Assert returned data matches input"
    ],
    "why": "Core CRUD - products must be persistable",
    "priority": "high"
  },
  {
    "name": "verify_create_product_validation",
    "description": "Verify product creation validates required fields",
    "requires": [],
    "steps_hint": [
      "POST /api/products with missing name",
      "Assert 400 response with error for 'name required'",
      "POST /api/products with negative price",
      "Assert 400 response with error for 'invalid price'"
    ],
    "why": "Data integrity - garbage in, garbage out",
    "priority": "high"
  },
  {
    "name": "verify_list_products_pagination",
    "description": "Verify product listing supports pagination",
    "requires": ["postgres"],
    "steps_hint": [
      "Create 15 products",
      "GET /api/products?page=1&limit=10",
      "Assert 10 items returned with pagination metadata",
      "GET /api/products?page=2&limit=10",
      "Assert 5 items returned"
    ],
    "why": "Large catalogs need pagination for performance",
    "priority": "medium"
  }
]
```

### New Validation

**Task**: "Add email format validation to registration"

```json
[
  {
    "name": "verify_email_format_rejected",
    "description": "Verify registration rejects malformed emails",
    "requires": [],
    "steps_hint": [
      "POST /api/register with email='not-an-email'",
      "Assert 400 response",
      "Assert error message mentions email format",
      "Verify no user created (if possible)"
    ],
    "why": "Email validation was the task goal",
    "priority": "high"
  },
  {
    "name": "verify_valid_email_accepted",
    "description": "Verify registration accepts valid emails",
    "requires": ["postgres"],
    "steps_hint": [
      "POST /api/register with email='valid@example.com'",
      "Assert 201 response",
      "GET /api/users/{id} to verify user created"
    ],
    "why": "Ensure validation doesn't over-block",
    "priority": "high"
  },
  {
    "name": "verify_edge_case_emails",
    "description": "Verify edge case email formats",
    "requires": [],
    "steps_hint": [
      "POST /api/register with email='user+tag@example.com' (valid)",
      "Assert 201",
      "POST /api/register with email='user@localhost' (depends on rules)",
      "Document actual behavior"
    ],
    "why": "Edge cases reveal spec ambiguity",
    "priority": "medium"
  }
]
```

### Modified Business Logic

**Task**: "Update order total to include tax"

```json
[
  {
    "name": "verify_order_includes_tax",
    "description": "Verify order total includes calculated tax",
    "requires": ["postgres"],
    "steps_hint": [
      "Create product with price $100",
      "Create order with 1x product",
      "GET /api/orders/{id}",
      "Assert total = $100 + tax (e.g., $110 for 10% tax)",
      "Assert tax_amount field present and correct"
    ],
    "why": "Tax calculation was the task goal",
    "priority": "high"
  },
  {
    "name": "verify_tax_rate_configurable",
    "description": "Verify tax rate respects configuration",
    "requires": ["postgres"],
    "steps_hint": [
      "Set TAX_RATE env var to 0.15 (15%)",
      "Create order",
      "Assert tax calculated at 15%"
    ],
    "why": "Tax rates vary by jurisdiction",
    "priority": "medium"
  }
]
```

### Auth/Permission Changes

**Task**: "Add admin-only endpoint for user deletion"

```json
[
  {
    "name": "verify_admin_can_delete_user",
    "description": "Verify admin can delete users",
    "requires": ["postgres"],
    "steps_hint": [
      "Create regular user A",
      "Login as admin",
      "DELETE /api/admin/users/{userA_id}",
      "Assert 200 or 204 response",
      "GET /api/users/{userA_id}",
      "Assert 404 (user deleted)"
    ],
    "why": "Core admin functionality",
    "priority": "high"
  },
  {
    "name": "verify_non_admin_cannot_delete",
    "description": "Verify regular users cannot delete users",
    "requires": [],
    "steps_hint": [
      "Login as regular user",
      "DELETE /api/admin/users/{some_id}",
      "Assert 403 Forbidden",
      "Verify user not deleted"
    ],
    "why": "Security - privilege escalation prevention",
    "priority": "high"
  },
  {
    "name": "verify_unauthenticated_rejected",
    "description": "Verify unauthenticated requests rejected",
    "requires": [],
    "steps_hint": [
      "DELETE /api/admin/users/{id} without auth header",
      "Assert 401 Unauthorized"
    ],
    "why": "Security baseline",
    "priority": "high"
  }
]
```

---

## Mapping environment.json to Scenarios

Use `environment.json` to understand available infrastructure:

### Databases → Persistence Tests

```json
// environment.json
{
  "databases": [
    {"name": "postgres", "type": "postgres", "required": true}
  ]
}
```

**Design implication**: Include persistence verification. Use `requires: ["postgres"]`.

### Services → Integration Tests

```json
// environment.json
{
  "services": [
    {"name": "redis", "type": "redis", "purpose": "session cache"},
    {"name": "email_service", "type": "http", "url_env": "EMAIL_SERVICE_URL"}
  ]
}
```

**Design implications**:
- Test session persistence in Redis if auth-related
- Verify email_service called (or mock it) for email-related features

### Scripts → Setup/Teardown

```json
// environment.json
{
  "scripts": {
    "setup_env": "harness/scripts/setup-env.sh",
    "seed_data": "harness/scripts/seed-data.sh"
  }
}
```

**Design implication**: Reference seeded data in scenarios. Example: "GET /api/products should return seeded products".

---

## Anti-Patterns

| Anti-Pattern | Why Bad | Do Instead |
|--------------|---------|------------|
| Testing only happy path | Misses bugs in error handling | Include error and edge cases |
| Using placeholder data | `{"name": "test", "email": "a@a.com"}` is unrealistic | Use realistic fake data |
| Skipping side effect checks | Data not actually persisted | Verify GET after POST |
| Too many scenarios | Wastes time, overwhelms verifier | Focus on 2-5 critical scenarios |
| No `why` field | Verifier doesn't know intent | Always explain business reason |
| Requiring unavailable infra | Scenario will be skipped | Check environment.json first |

---

## Decision Tree

```
Task completed → Analyze files_changed
    │
    ├─ New endpoint?
    │   └─ Design: create/read/error scenarios
    │
    ├─ Modified endpoint?
    │   └─ Design: verify new behavior + regression
    │
    ├─ New validation?
    │   └─ Design: valid/invalid/edge scenarios
    │
    ├─ Auth change?
    │   └─ Design: permitted/denied/unauthenticated scenarios
    │
    ├─ Business logic change?
    │   └─ Design: verify calculation/rule applied
    │
    └─ Pure refactor (no behavior change)?
        └─ Design: regression scenarios only (or skip)
```

---

## Handoff to Verifier

After designing scenarios, pass them to the Verifier Subagent:

```
Agent(
    description="Verify: functional checks for [task]",
    prompt="""
...

## Task-Specific Scenarios (designed by Coordinator)
{task_specific_scenarios as JSON}

Execute ALL task-specific scenarios. They verify THIS task's changes.
"""
)
```

The Verifier:
1. Reads the `steps_hint`
2. Inspects the code for exact request format
3. Generates realistic test data
4. Executes and reports with evidence
