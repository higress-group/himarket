# Runtime Verification Schema (Optional)

> **Note**: `verify.json` is an **optional** configuration file used by the standalone `verify.py` tool. The primary verification mechanism in the harness-executor skill flow is the **Verifier Subagent**, which does not depend on `verify.json`. This schema is documented for developers who want to use `verify.py` independently for quick smoke checks.

Configuration schema for `harness/config/verify.json`.

## Overview

Runtime verification validates that the application **actually works** after code changes, not just that it compiles and passes static tests. This complements the static validation pipeline (`validate.py`).

## Schema Definition

```json
{
  "version": "1.0",
  "app_type": "server" | "cli" | "frontend" | "library" | "hybrid",
  "auto_detected": true,
  "prerequisites": { ... },
  "verification": {
    "server": { ... },
    "cli": { ... },
    "frontend": { ... }
  },
  "smoke_tests": [ ... ],
  "cleanup": { ... }
}
```

## Full Schema with Types

```typescript
interface VerifyConfig {
  // Schema version for forward compatibility
  version: "1.0";

  // Primary application type (auto-detected or manual)
  app_type: "server" | "cli" | "frontend" | "library" | "hybrid";

  // Whether this config was auto-generated
  auto_detected: boolean;

  // Environment prerequisites - checked BEFORE running verification
  prerequisites?: Prerequisites;

  // Type-specific verification settings
  verification: {
    server?: ServerVerification;
    cli?: CLIVerification;
    frontend?: FrontendVerification;
  };

  // Quick sanity checks to run after verification setup
  smoke_tests: SmokeTest[];

  // Cleanup configuration
  cleanup: CleanupConfig;
}
```

---

## Prerequisites (Environment Preflight Checks)

Environment checks that MUST pass before runtime verification begins. If any prerequisite fails, verification is skipped with a clear error message explaining what's missing.

```typescript
interface Prerequisites {
  // Database connectivity checks
  databases?: DatabaseCheck[];

  // Required environment variables
  env_vars?: EnvVarCheck[];

  // External services that must be reachable
  services?: ServiceCheck[];

  // Custom commands that must succeed
  commands?: CommandCheck[];

  // Files or directories that must exist
  paths?: PathCheck[];

  // Skip all prerequisites (for CI without full env)
  skip?: boolean;
}

interface DatabaseCheck {
  name: string;                    // e.g., "MySQL", "PostgreSQL", "Redis"
  type: "mysql" | "postgres" | "redis" | "mongodb" | "sqlite" | "custom";
  // Connection info - either via env var or explicit
  connection_string_env?: string;  // e.g., "DB_URL" - reads from env var
  host_env?: string;               // e.g., "DB_HOST"
  port_env?: string;               // e.g., "DB_PORT"
  host?: string;                   // Direct value: "localhost"
  port?: number;                   // Direct value: 3306
  // For custom type, a command to test connectivity
  check_command?: string;          // e.g., "mysql -h $DB_HOST -P $DB_PORT -u $DB_USER -e 'SELECT 1'"
  required: boolean;               // If false, failure is a warning
  timeout_seconds?: number;        // Default: 5
}

interface EnvVarCheck {
  name: string;                    // The env var name
  description?: string;            // Human-readable purpose
  required: boolean;
  pattern?: string;                // Regex the value must match
  not_empty?: boolean;             // If true, var must have non-empty value
}

interface ServiceCheck {
  name: string;                    // e.g., "Auth Service", "Redis Cache"
  type: "http" | "tcp" | "grpc";
  url?: string;                    // For http: "http://localhost:9000/health"
  host?: string;                   // For tcp/grpc
  port?: number;
  expected_status?: number;        // For http, default 200
  required: boolean;
  timeout_seconds?: number;        // Default: 5
}

interface CommandCheck {
  name: string;                    // e.g., "Docker running"
  command: string;                 // e.g., "docker info > /dev/null 2>&1"
  expected_exit_code?: number;     // Default: 0
  required: boolean;
  timeout_seconds?: number;        // Default: 10
}

interface PathCheck {
  path: string;                    // Relative to project root or absolute
  type: "file" | "directory";
  required: boolean;
  description?: string;
}
```

### Prerequisites Examples

#### Example 1: Java Spring Boot with MySQL

```json
{
  "prerequisites": {
    "databases": [
      {
        "name": "MySQL Database",
        "type": "mysql",
        "host_env": "DB_HOST",
        "port_env": "DB_PORT",
        "required": true,
        "timeout_seconds": 5
      }
    ],
    "env_vars": [
      {"name": "DB_HOST", "required": true, "not_empty": true},
      {"name": "DB_PORT", "required": true, "pattern": "^\\d+$"},
      {"name": "DB_USERNAME", "required": true},
      {"name": "DB_PASSWORD", "required": true},
      {"name": "JWT_SECRET", "required": true, "description": "JWT signing key"}
    ]
  }
}
```

#### Example 2: Node.js with Redis and External API

```json
{
  "prerequisites": {
    "services": [
      {
        "name": "Redis Cache",
        "type": "tcp",
        "host": "localhost",
        "port": 6379,
        "required": true
      },
      {
        "name": "Auth Service",
        "type": "http",
        "url": "http://localhost:9000/health",
        "expected_status": 200,
        "required": false
      }
    ],
    "env_vars": [
      {"name": "NODE_ENV", "required": false},
      {"name": "API_KEY", "required": true, "description": "External API key"}
    ]
  }
}
```

#### Example 3: Docker-dependent service

```json
{
  "prerequisites": {
    "commands": [
      {
        "name": "Docker daemon running",
        "command": "docker info > /dev/null 2>&1",
        "required": true
      },
      {
        "name": "Docker Compose available",
        "command": "docker compose version > /dev/null 2>&1",
        "required": true
      }
    ],
    "paths": [
      {
        "path": "docker-compose.yml",
        "type": "file",
        "required": true,
        "description": "Docker Compose configuration"
      }
    ]
  }
}
```

#### Example 4: Skip prerequisites in CI

```json
{
  "prerequisites": {
    "skip": true
  }
}
```

---

## Server Verification

For backend services (HTTP APIs, gRPC, WebSocket servers).

```typescript
interface ServerVerification {
  // How to start the server
  start: {
    command: string;           // e.g., "go run cmd/server/main.go"
    working_dir?: string;      // Relative to project root
    env?: Record<string, string>;
    args?: string[];
    background: true;          // Always true for servers
  };

  // How to know server is ready
  readiness: {
    type: "http" | "tcp" | "log_pattern" | "command";

    // For type: "http"
    endpoint?: string;         // e.g., "http://localhost:8080/health"
    expected_status?: number;  // Default: 200

    // For type: "tcp"
    host?: string;             // Default: "localhost"
    port?: number;

    // For type: "log_pattern"
    pattern?: string;          // Regex to match in stdout/stderr

    // For type: "command"
    command?: string;          // Command that succeeds when ready

    timeout_seconds: number;   // Max wait time, default: 30
    poll_interval_ms: number;  // Check frequency, default: 500
  };

  // API endpoints to test
  endpoints: EndpointTest[];

  // How to stop the server
  stop: {
    signal: "SIGTERM" | "SIGINT" | "SIGKILL";
    graceful_timeout_seconds: number;  // Default: 5
  };
}

interface EndpointTest {
  name: string;
  method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH";
  path: string;
  headers?: Record<string, string>;
  body?: any;
  expected: {
    status: number | number[];  // e.g., 200 or [200, 201]
    body_contains?: string[];
    body_not_contains?: string[];
    json_path?: JsonPathAssertion[];
  };
  timeout_seconds?: number;
}

interface JsonPathAssertion {
  path: string;        // e.g., "$.data.id"
  operator: "exists" | "equals" | "contains" | "type";
  value?: any;
}
```

### Server Example

```json
{
  "server": {
    "start": {
      "command": "go run cmd/server/main.go",
      "env": { "PORT": "8081", "ENV": "test" },
      "background": true
    },
    "readiness": {
      "type": "http",
      "endpoint": "http://localhost:8081/health",
      "expected_status": 200,
      "timeout_seconds": 30
    },
    "endpoints": [
      {
        "name": "health check",
        "method": "GET",
        "path": "/health",
        "expected": { "status": 200 }
      },
      {
        "name": "create user",
        "method": "POST",
        "path": "/api/users",
        "headers": { "Content-Type": "application/json" },
        "body": { "name": "test", "email": "test@example.com" },
        "expected": {
          "status": [200, 201],
          "json_path": [
            { "path": "$.id", "operator": "exists" },
            { "path": "$.name", "operator": "equals", "value": "test" }
          ]
        }
      }
    ],
    "stop": {
      "signal": "SIGTERM",
      "graceful_timeout_seconds": 5
    }
  }
}
```

---

## CLI Verification

For command-line tools and scripts.

```typescript
interface CLIVerification {
  // The CLI executable
  binary: {
    build_command?: string;    // e.g., "go build -o bin/cli cmd/cli/main.go"
    path: string;              // e.g., "bin/cli" or "node dist/cli.js"
  };

  // Commands to test
  commands: CLICommandTest[];
}

interface CLICommandTest {
  name: string;
  args: string[];              // e.g., ["--version"]
  stdin?: string;              // Input to pipe to the command
  env?: Record<string, string>;
  expected: {
    exit_code: number | number[];  // Usually 0 for success
    stdout_contains?: string[];
    stdout_not_contains?: string[];
    stdout_matches?: string;       // Regex pattern
    stderr_contains?: string[];
    file_created?: string[];       // Paths that should exist after
    file_content?: FileContentAssertion[];
  };
  timeout_seconds?: number;
}

interface FileContentAssertion {
  path: string;
  contains?: string[];
  matches?: string;  // Regex
}
```

### CLI Example

```json
{
  "cli": {
    "binary": {
      "build_command": "go build -o bin/mycli cmd/cli/main.go",
      "path": "bin/mycli"
    },
    "commands": [
      {
        "name": "version flag",
        "args": ["--version"],
        "expected": {
          "exit_code": 0,
          "stdout_matches": "v\\d+\\.\\d+\\.\\d+"
        }
      },
      {
        "name": "help flag",
        "args": ["--help"],
        "expected": {
          "exit_code": 0,
          "stdout_contains": ["Usage:", "Commands:"]
        }
      },
      {
        "name": "generate config",
        "args": ["init", "--output", "/tmp/test-config.json"],
        "expected": {
          "exit_code": 0,
          "file_created": ["/tmp/test-config.json"],
          "file_content": [
            { "path": "/tmp/test-config.json", "contains": ["version"] }
          ]
        }
      }
    ]
  }
}
```

---

## Frontend Verification

For web applications using Chrome DevTools Protocol (CDP).

```typescript
interface FrontendVerification {
  // How to start the dev server
  dev_server: {
    command: string;           // e.g., "npm run dev"
    working_dir?: string;
    env?: Record<string, string>;
    background: true;
  };

  // How to know dev server is ready
  readiness: {
    type: "http" | "log_pattern";
    url?: string;              // e.g., "http://localhost:3000"
    pattern?: string;
    timeout_seconds: number;
  };

  // Chrome/Chromium configuration
  browser: {
    executable?: string;       // Path to Chrome, auto-detected if omitted
    headless: boolean;         // Default: true
    args?: string[];           // Additional Chrome args
  };

  // Page tests using CDP
  pages: PageTest[];

  // How to stop the dev server
  stop: {
    signal: "SIGTERM" | "SIGINT";
    graceful_timeout_seconds: number;
  };
}

interface PageTest {
  name: string;
  url: string;                 // Full URL or path (appended to dev server URL)
  wait_for?: WaitCondition;
  actions?: PageAction[];
  assertions: PageAssertion[];
  screenshot?: {
    path: string;              // Where to save screenshot
    on_failure_only?: boolean;
  };
}

interface WaitCondition {
  type: "selector" | "navigation" | "network_idle" | "timeout";
  selector?: string;
  timeout_ms?: number;
}

interface PageAction {
  type: "click" | "type" | "select" | "wait" | "scroll" | "evaluate";
  selector?: string;
  value?: string;
  script?: string;             // For type: "evaluate"
  timeout_ms?: number;
}

interface PageAssertion {
  type: "element_exists" | "element_text" | "element_visible" |
        "no_console_errors" | "no_network_errors" | "title" | "url" | "evaluate";
  selector?: string;
  expected?: string | string[];
  script?: string;             // For type: "evaluate", returns boolean
}
```

### Frontend Example

```json
{
  "frontend": {
    "dev_server": {
      "command": "npm run dev",
      "env": { "PORT": "3001" },
      "background": true
    },
    "readiness": {
      "type": "http",
      "url": "http://localhost:3001",
      "timeout_seconds": 60
    },
    "browser": {
      "headless": true,
      "args": ["--no-sandbox", "--disable-gpu"]
    },
    "pages": [
      {
        "name": "homepage loads",
        "url": "/",
        "wait_for": { "type": "selector", "selector": "#app" },
        "assertions": [
          { "type": "element_exists", "selector": "#app" },
          { "type": "no_console_errors" },
          { "type": "title", "expected": "My App" }
        ]
      },
      {
        "name": "login flow",
        "url": "/login",
        "wait_for": { "type": "selector", "selector": "form" },
        "actions": [
          { "type": "type", "selector": "#email", "value": "test@example.com" },
          { "type": "type", "selector": "#password", "value": "password123" },
          { "type": "click", "selector": "button[type=submit]" },
          { "type": "wait", "timeout_ms": 2000 }
        ],
        "assertions": [
          { "type": "url", "expected": "/dashboard" },
          { "type": "element_visible", "selector": ".welcome-message" }
        ],
        "screenshot": {
          "path": "harness/screenshots/login-success.png",
          "on_failure_only": false
        }
      }
    ],
    "stop": {
      "signal": "SIGTERM",
      "graceful_timeout_seconds": 5
    }
  }
}
```

---

## Smoke Tests

Quick sanity checks that run after the main verification. Useful for cross-cutting concerns.

```typescript
interface SmokeTest {
  name: string;
  type: "command" | "http" | "file_exists";

  // For type: "command"
  command?: string;
  expected_exit_code?: number;

  // For type: "http"
  url?: string;
  expected_status?: number;

  // For type: "file_exists"
  paths?: string[];

  required: boolean;  // If false, failure is a warning
}
```

### Smoke Tests Example

```json
{
  "smoke_tests": [
    {
      "name": "database migrations applied",
      "type": "command",
      "command": "go run cmd/migrate/main.go status",
      "expected_exit_code": 0,
      "required": true
    },
    {
      "name": "static assets built",
      "type": "file_exists",
      "paths": ["dist/index.html", "dist/main.js"],
      "required": true
    }
  ]
}
```

---

## Cleanup Configuration

```typescript
interface CleanupConfig {
  // Files/directories to remove after verification
  remove_paths?: string[];

  // Commands to run for cleanup
  commands?: string[];

  // Environment cleanup
  reset_env?: string[];  // Env vars to unset
}
```

---

## Auto-Detection Rules

`verify.py` auto-detects app type using these heuristics:

| Indicator | Detected Type |
|-----------|--------------|
| `cmd/server/` or `**/server.go` or `main.go` with `http.ListenAndServe` | server |
| `cmd/cli/` or `**/cli.go` or `cobra`/`urfave/cli` imports | cli |
| `package.json` with `react`/`vue`/`next`/`vite` | frontend |
| `setup.py`/`pyproject.toml` without entry points | library |
| Multiple of above | hybrid |

For hybrid apps, verify.json should contain multiple sections.

---

## Standalone Usage

`verify.py` can be run independently as a standalone tool (it is **not** part of the skill execution flow — the Verifier Subagent handles that):

```bash
# Full runtime verification
python3 $SKILL_DIR/scripts/verify.py . --json

# Specific type only
python3 $SKILL_DIR/scripts/verify.py . --type server

# Skip cleanup (for debugging)
python3 $SKILL_DIR/scripts/verify.py . --no-cleanup

# Verbose output
python3 $SKILL_DIR/scripts/verify.py . -v
```

Exit codes:
- 0: All verifications passed
- 1: One or more required verifications failed

---

## Functional Verification Report Schema

The verifier subagent produces `harness/trace/verification-report.json` with the following schema. This is separate from `verify.json` — it's an **output**, not a config file.

```typescript
interface VerificationReport {
  overall_status: "pass" | "partial" | "fail" | "skip";
  skip_reason?: string;               // Only if overall_status == "skip"

  server: {
    started: boolean;
    ready_in_seconds: number;
    stopped_cleanly: boolean;
  };

  task_specific_scenarios: ScenarioResult[];   // Scenarios designed by Coordinator
  predefined_scenarios: ScenarioResult[];      // Pre-defined scenarios from environment.json
  additional_checks: ScenarioResult[];         // Extra checks generated by verifier

  claims: Claim[];
  summary: VerificationSummary;
  timing: VerificationTiming;
}

interface ScenarioResult {
  name: string;                    // From environment.json functional_scenarios[].name
  status: "pass" | "fail" | "skipped";
  skip_reason?: string;            // Only if skipped
  steps: VerificationStep[];
  evidence: string;                // Summary of what was observed
  duration_seconds: number;
}

interface VerificationStep {
  description: string;             // What this step tested
  request: {
    method: string;
    url: string;
    headers?: Record<string, string>;
    body?: any;
  };
  response: {
    status: number;
    body?: any;
  };
  assertion: string;               // What was expected
  passed: boolean;
}

interface Claim {
  claim: string;                   // What is being claimed
  type: "factual" | "process" | "quality";
  verified: boolean;
  evidence: string;
}

interface VerificationSummary {
  task_specific_total: number;
  task_specific_passed: number;
  predefined_total: number;
  predefined_passed: number;
  additional_checks_total: number;
  additional_checks_passed: number;
  pass_rate: number;               // 0.0 to 1.0
}

interface VerificationTiming {
  total_seconds: number;
  server_startup_seconds?: number;
  verification_seconds?: number;
  cleanup_seconds?: number;
}
```

### Relationship to Other Schemas

| File | Producer | Consumer | Purpose |
|------|----------|----------|---------|
| `verify.json` | harness-creator or auto-detect | verify.py (standalone tool) | Optional config for standalone smoke checks |
| `environment.json` | harness-creator | preflight.py, verifier subagent | Environment description |
| `verification-report.json` | verifier subagent | coordinator, task_state.py | Functional verification results (the completion gate) |
- 2: Configuration error or setup failure
