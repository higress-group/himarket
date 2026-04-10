# Task-Aware Verification Guide (Optional Enhancement)

> **Note**: This is an **optional enhancement** tool. The primary verification mechanism is the **Verifier Subagent** (see `functional-verification-guide.md`), which dynamically designs and executes verification based on task context. The `generate_task_verification.py` script documented here is a supplementary tool that can pre-populate `verify.json` for use with the standalone `verify.py` tool.

Runtime verification should test **what was actually changed**, not just generic health checks. This guide explains how to generate task-specific verification tests using the `generate_task_verification.py` script.

## Problem: Generic Verification is Insufficient

Default verify.json only tests:
- Health endpoint (GET /health → 200)
- `--version` and `--help` flags

This misses the actual functionality you implemented:
- New API endpoints (POST /api/users)
- Modified business logic (updated validation rules)
- New CLI commands (export, migrate, sync)

## Solution: Task-Aware Test Generation

`generate_task_verification.py` can optionally be run to:

1. **Analyze changed files** → detect new routes, commands, components
2. **Parse task description** → extract expected behaviors
3. **Generate targeted tests** → test what was actually built
4. **Update verify.json** → add new test cases automatically

## How It Works

### Input: Task Context

The generator needs these inputs:

| Input | Source | Example |
|-------|--------|---------|
| `--description` | Plan file Goal section | "Implement user registration API" |
| `--goal` | Plan file Goal section | "Users can register via POST /api/users" |
| `--files-changed` | Subagent JSON result | `internal/handler/user.go internal/service/user.go` |
| `--files-created` | Subagent JSON result | `internal/handler/register.go` |

### Processing: Code Analysis

The script analyzes changed files to detect:

**HTTP Routes (Go, TypeScript, Python):**
```go
// Detected: POST /api/users
r.Post("/api/users", handler.CreateUser)
```

**CLI Commands (Cobra, Click, Typer):**
```go
// Detected: command "export"
&cobra.Command{Use: "export", ...}
```

### Processing: Description Analysis

The script extracts expectations from the task description:

| Pattern | Detected Behavior |
|---------|-------------------|
| "implement user registration" | POST /users endpoint |
| "add export command" | CLI command "export" |
| "create login flow" | POST /login endpoint |
| "list all products" | GET /products endpoint |

### Output: Verification Suggestions

```json
{
  "suggestions": [
    {
      "type": "endpoint",
      "name": "post_api_users",
      "config": {
        "name": "post_api_users",
        "method": "POST",
        "path": "/api/users",
        "headers": {"Content-Type": "application/json"},
        "body": {},
        "expected": {"status": [200, 201, 400]}
      },
      "confidence": 0.9,
      "reason": "Matches expected POST /users from task description"
    }
  ],
  "updated_config": { ... }
}
```

### Confidence Scoring

| Score | Meaning | Action |
|-------|---------|--------|
| 0.9+ | High confidence: code + description match | Auto-add to verify.json |
| 0.6-0.9 | Medium: detected in code, no description match | Auto-add with basic assertions |
| < 0.6 | Low: only from description, not found in code | Suggest but don't auto-add |

## Usage

### Standalone Usage (Optional)

```bash
# Extract context from completed phases
TASK_DESCRIPTION="Implement user registration with email validation"
FILES_CHANGED="internal/handler/user.go internal/service/user.go"
FILES_CREATED="internal/handler/register.go"

# Generate task-aware verification config
python3 "$SKILL_DIR/scripts/generate_task_verification.py" . \
  --description "$TASK_DESCRIPTION" \
  --files-changed $FILES_CHANGED \
  --files-created $FILES_CREATED

# Then optionally run standalone smoke check with the updated config
python3 "$SKILL_DIR/scripts/verify.py" . --json
```

### CLI Options

```bash
# Basic usage
python3 generate_task_verification.py . -d "task description"

# With all context
python3 generate_task_verification.py . \
  -d "Implement user CRUD" \
  -g "Users can create, read, update, delete accounts" \
  --files-changed file1.go file2.go \
  --files-created file3.go

# Dry run (show suggestions without updating verify.json)
python3 generate_task_verification.py . -d "..." --dry-run

# JSON output for programmatic use
python3 generate_task_verification.py . -d "..." --json

# Adjust confidence threshold (default 0.6)
python3 generate_task_verification.py . -d "..." --confidence-threshold 0.8
```

## Supported Patterns

### HTTP Routes Detection

**Go (chi, gin, echo, gorilla, net/http):**
```go
r.Get("/api/users", ...)           // chi
r.GET("/api/users", ...)           // gin
e.GET("/api/users", ...)           // echo
r.HandleFunc("/api/users", ...).Methods("GET")  // gorilla
http.HandleFunc("/api/users", ...) // net/http
```

**TypeScript/JavaScript (Express, Fastify):**
```typescript
app.get('/api/users', ...)
router.post('/api/users', ...)
@Get('/api/users')   // NestJS decorator
```

**Python (FastAPI, Flask):**
```python
@app.get("/api/users")
@app.route("/api/users", methods=["GET"])
```

### CLI Commands Detection

**Go (Cobra, urfave/cli):**
```go
&cobra.Command{Use: "export", ...}
&cli.Command{Name: "export", ...}
```

**Python (Click, Typer):**
```python
@click.command(name="export")
@app.command(name="export")
```

## Task Description Patterns

The generator recognizes these patterns in task descriptions:

### CRUD Operations
| Phrase | Detected |
|--------|----------|
| "create user", "add user", "register user" | POST /users |
| "list users", "get all users" | GET /users |
| "get user by id", "fetch user" | GET /users/{id} |
| "update user", "modify user" | PUT /users/{id} |
| "delete user", "remove user" | DELETE /users/{id} |

### Authentication
| Phrase | Detected |
|--------|----------|
| "login", "authenticate" | POST /login |
| "logout", "sign out" | POST /logout |
| "register", "signup" | POST /register |

### CLI Commands
| Phrase | Detected |
|--------|----------|
| "export to csv" | export --format csv |
| "import data" | import |
| "run migration" | migrate |
| "sync data" | sync |

## Examples

### Example 1: User Registration API

**Task:** "Implement user registration with email validation"

**Files changed:** `internal/handler/auth.go`
```go
r.Post("/api/v1/register", h.Register)
```

**Generated tests:**
```json
{
  "endpoints": [
    {
      "name": "register_user",
      "method": "POST",
      "path": "/api/v1/register",
      "headers": {"Content-Type": "application/json"},
      "body": {},
      "expected": {"status": [200, 201, 400]}
    }
  ]
}
```

### Example 2: CLI Export Command

**Task:** "Add export command to dump data to CSV"

**Files changed:** `cmd/cli/export.go`
```go
&cobra.Command{Use: "export", ...}
```

**Generated tests:**
```json
{
  "commands": [
    {
      "name": "test_export",
      "args": ["export"],
      "expected": {"exit_code": 0}
    },
    {
      "name": "test_export_help",
      "args": ["export", "--help"],
      "expected": {"exit_code": 0, "stdout_contains": ["Usage", "export"]}
    }
  ]
}
```

## Integration with Verification Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│ Optional: Using generate_task_verification.py               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Run generate_task_verification.py with task context     │
│     └─ Updates harness/config/verify.json                   │
│                                                             │
│  2. Run verify.py for standalone smoke check (optional)     │
│     └─ Tests: health + task-specific endpoints              │
│                                                             │
│  Note: The primary verification mechanism is the Verifier   │
│  Subagent, which does not depend on verify.json.            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Metadata Tracking

The generator adds `_task_verification` metadata to verify.json for traceability:

```json
{
  "_task_verification": {
    "task_description": "Implement user registration...",
    "files_analyzed": ["internal/handler/auth.go", "internal/service/auth.go"],
    "routes_detected": 3,
    "commands_detected": 0,
    "suggestions_count": 4,
    "auto_added_count": 3
  }
}
```

This helps track:
- What task generated these tests
- Which files were analyzed
- How many tests were auto-added vs suggested

## Limitations and Future Work

### Current Limitations

1. **Test data generation**: Creates placeholder `body: {}` — doesn't generate realistic test data
2. **Authentication**: Doesn't automatically add auth headers to protected endpoints
3. **Frontend**: Focuses on backend (routes, CLI); frontend component detection is limited
4. **Complex assertions**: Generates basic status checks; complex JSON schema validation not yet supported

### Future Enhancements

1. **Smart test data**: Analyze request schemas to generate valid test payloads
2. **Auth detection**: Detect middleware decorators and add appropriate tokens
3. **Contract testing**: Extract OpenAPI specs and validate against them
4. **Frontend analysis**: Detect React/Vue components and generate page tests

## Troubleshooting

### No routes detected but I added endpoints

- Check file extension is supported (.go, .ts, .js, .py)
- Verify the routing pattern matches supported frameworks
- Check if the file is in `--files-changed` or `--files-created`
- Use `--dry-run --json` to see what was analyzed

### Low confidence scores

- Add more context to `--description` and `--goal`
- Use explicit phrases like "implement POST /api/users"
- Verify the detected routes match your actual code

### Tests fail with 401/403

- The generator doesn't auto-add auth headers
- Manually edit verify.json to add `Authorization` headers
- Or configure a test user/token in prerequisites

---

## Integration with Functional Verification

`generate_task_verification.py` is an **optional enhancement** tool that generates `verify.json` config for the standalone `verify.py` tool.

The **primary verification mechanism** is the **Verifier Subagent**, which:
- Starts the actual application
- Makes real HTTP requests
- Verifies side effects and data persistence
- Produces `verification-report.json` (the completion gate)

### When to Use Which

| Scenario | Tool | Notes |
|----------|------|-------|
| Quick standalone smoke check | `verify.py` | Optional, not part of skill flow |
| All functional verification | Verifier Subagent | **PRIMARY** — the completion gate |

The Verifier Subagent handles all verification needs: endpoint checks, input validation, multi-step flows, CRUD lifecycles, CLI commands, and side-effect verification.

> **Note**: The skill flow uses ONLY the Verifier Subagent. The `generate_task_verification.py` + `verify.py` pipeline is available as an optional standalone tool for developers who want quick smoke checks outside the skill flow.
