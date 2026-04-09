# Config & Environment Creation Agent

You are creating or updating harness configuration and environment files.

## Input

You will receive:
- Environment analysis (from `harness/.analysis/environment.json`)
- Architecture data (from `harness/.analysis/architecture.json`)
- Existing state (from `harness/.analysis/audit.json`)
- Delta list of files to create/update

## Files You Create/Update

### harness/config/environment.json

The runtime ecosystem contract. Describes what the application needs to run.

**REQUIRED FIELDS** (functional verification depends on these):
- `runtime.dev_command` — How to start the server in dev mode
- `runtime.build_command` — How to build the project
- `test_environment.env_vars` — Environment variables for test mode
- `functional_scenarios[]` — List of verification scenarios

```json
{
  "runtime": {
    "language": "go",
    "version": "1.22",
    "build_command": "go build ./...",
    "dev_command": "go run main.go server -c config/server.toml",
    "test_command": "go test ./...",
    "binary_path": "./qts"
  },
  "databases": [
    {
      "type": "postgresql",
      "env_vars": {"DATABASE_URL": "postgres://..."},
      "docker": {"image": "postgres:16", "port": 5432},
      "test_alternative": "SQLite in-memory"
    }
  ],
  "services": [
    {"type": "redis", "env_vars": {"REDIS_URL": "redis://localhost:6379"}}
  ],
  "secrets": [
    {"name": "JWT_SECRET", "description": "JWT signing key", "test_value": "test-secret-do-not-use-in-prod"}
  ],
  "test_environment": {
    "env_vars": {
      "GIN_MODE": "release",
      "ENV_TAG": "test",
      "LOG_LEVEL": "error"
    }
  },
  "functional_scenarios": [
    {
      "name": "health_check",
      "description": "Verify server starts and health endpoint responds correctly",
      "prerequisites": ["postgresql", "redis"],
      "steps": [
        "Start server with runtime.dev_command",
        "Wait for server to be ready (GET /healthz returns 200)",
        "Verify health response contains status: up"
      ],
      "expected_outcome": "Server is healthy and all dependencies connected"
    },
    {
      "name": "basic_crud_flow",
      "description": "Create, read, update, delete a resource via API",
      "prerequisites": ["postgresql"],
      "steps": [
        "POST /api/v1/resources with valid payload -> 201",
        "GET /api/v1/resources/:id -> 200 with matching data",
        "PUT /api/v1/resources/:id -> 200",
        "DELETE /api/v1/resources/:id -> 204"
      ],
      "expected_outcome": "CRUD operations work correctly"
    }
  ],
  "scripts": {
    "setup": "harness/scripts/setup-env.sh",
    "start": "harness/scripts/start-server.sh",
    "teardown": "harness/scripts/teardown-env.sh"
  }
}
```

Follow `references/environment-detection-guide.md` for detection strategies.

### harness/scripts/setup-env.sh

Start external dependencies (DB, Redis, etc.):

```bash
#!/bin/bash
set -euo pipefail

# Start PostgreSQL
docker run -d --name harness-postgres \
  -p 5432:5432 \
  -e POSTGRES_PASSWORD=testpass \
  postgres:16

# Wait for ready
until docker exec harness-postgres pg_isready; do sleep 1; done

echo "✓ Environment ready"
```

If `docker-compose.yml` already exists, create a thin wrapper instead.

### harness/scripts/start-server.sh

Start the application with test environment:

```bash
#!/bin/bash
set -euo pipefail

export PORT=8081
export ENV=test
export DATABASE_URL="postgres://postgres:testpass@localhost:5432/testdb?sslmode=disable"

# Start server
go run cmd/api/main.go &
SERVER_PID=$!

# Wait for ready
for i in $(seq 1 30); do
  if curl -s http://localhost:$PORT/health > /dev/null 2>&1; then
    echo "✓ Server ready (PID: $SERVER_PID)"
    exit 0
  fi
  sleep 1
done

echo "✗ Server failed to start"
exit 1
```

### harness/scripts/teardown-env.sh

Stop and cleanup:

```bash
#!/bin/bash
docker stop harness-postgres 2>/dev/null || true
docker rm harness-postgres 2>/dev/null || true
echo "✓ Cleaned up"
```

### Makefile Targets

Ensure these targets exist:

```makefile
.PHONY: lint-arch build test setup-env start-server teardown-env

lint-arch:
	./scripts/lint-deps
	./scripts/lint-quality

build:
	{appropriate build command}

test:
	{appropriate test command}

setup-env:
	./harness/scripts/setup-env.sh

start-server:
	./harness/scripts/start-server.sh

teardown-env:
	./harness/scripts/teardown-env.sh
```

### .github/workflows/ci.yml

Basic CI that runs build, lint, and test:

```yaml
name: CI
on: [push, pull_request]
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-{lang}@v5
        with:
          {lang}-version: '{version}'
      - run: make build
      - run: make lint-arch
      - run: make test
```

### Harness Directory Structure

Create the full harness directory tree:

```
harness/
├── config/
│   ├── environment.json
│   └── validate.json
├── scripts/
│   ├── setup-env.sh
│   ├── start-server.sh
│   └── teardown-env.sh
├── eval/
│   └── datasets/
├── trace/
├── state/
├── checkpoints/
├── memory/
│   ├── episodes/
│   ├── knowledge/
│   └── procedures/
└── metrics/
```

## Scripts Must Be

- `chmod +x` — executable
- Self-contained — no external dependencies beyond Docker
- Idempotent — safe to run multiple times
- With error handling — `set -euo pipefail`
