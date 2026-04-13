# Trajectory Compilation: From Agent Runs to Deterministic Scripts

> "The Harness-as-Policy variant (no LLM at inference time) outperforms both Gemini-2.5-Pro and GPT-5.2-High on 1-player games." — AutoHarness paper

The most surprising result from the AutoHarness paper: for certain tasks, a **pure code policy** (no LLM reasoning at inference time) can outperform the best models while costing essentially $0 per execution. This reference documents how to identify opportunities for trajectory compilation and how to implement them.

## The Core Insight

When an agent successfully completes the same type of task multiple times with a consistent pattern, that pattern can be "compiled" into a deterministic script. Future invocations skip the LLM entirely.

```
Traditional Flow:
  User request → LLM reasoning → Tool calls → Validation → Result
  Cost: $$$ per invocation, variable latency

Compiled Policy Flow:
  User request → Pattern match → Run script → Result
  Cost: ~$0 per invocation, consistent latency
```

## When Trajectory Compilation Applies

Not every task can be compiled. Good candidates have these characteristics:

| Characteristic | Good Candidate | Poor Candidate |
|---------------|----------------|----------------|
| Task variability | Low (same steps each time) | High (requires judgment) |
| Input structure | Predictable | Varies widely |
| Decision points | Few or none | Many trade-offs |
| Success pattern | Consistent across runs | Different approaches work |
| Frequency | Repeated often | Rare |

### Examples of Compilable Tasks

| Task Type | Why Compilable | Compiled Form |
|-----------|---------------|---------------|
| Add a new API endpoint | Steps are mechanical: types → service → handler → route → test | `make add-endpoint NAME=foo` |
| Create a new design doc | Template fill-in with predictable structure | `make new-design-doc COMPONENT=bar` |
| Run pre-commit checks | Same sequence every time | Already exists as `make lint-arch` |
| Generate changelog | Analyze git history, format output | `make changelog FROM=v1.0 TO=v1.1` |
| Scaffold a new package | Create standard files in standard locations | `make new-package PKG=internal/baz` |

### Examples of Non-Compilable Tasks

| Task Type | Why Not Compilable |
|-----------|-------------------|
| Implement a feature from spec | Requires understanding requirements, making design choices |
| Debug a failing test | Requires investigation, hypothesis testing |
| Refactor for performance | Requires profiling, trade-off analysis |
| Review and improve code | Requires judgment about quality |

## Detecting Compilation Opportunities

### Signal 1: Episodic Memory Shows Repetition

When the same pattern appears 3+ times in episodic memory:

```bash
# Search episodic memory for repeated patterns
grep -h "procedure" harness/memory/episodes/*.jsonl | sort | uniq -c | sort -rn | head -10
```

If you see:
```
5 "procedure": "add-api-endpoint"
4 "procedure": "create-design-doc"
3 "procedure": "scaffold-package"
```

These are compilation candidates.

### Signal 2: Procedural Memory with High Success Rate

Check `harness/memory/procedures/`:

```json
{
    "procedure": "Add a new API endpoint",
    "success_rate": "9/10",
    "steps": [
        {"step": 1, "action": "Define request/response types in internal/types/"},
        {"step": 2, "action": "Create service method in internal/core/"},
        {"step": 3, "action": "Add handler in api/handlers/"},
        {"step": 4, "action": "Register route in api/routes.go"},
        {"step": 5, "action": "Write tests for each layer"}
    ]
}
```

A procedure with 90%+ success rate and consistent steps is ready for compilation.

### Signal 3: Harness Critic Identifies Repetitive Work

The Critic may flag:

```json
{
    "pattern_type": "repetitive_task",
    "description": "Same 5-step sequence executed 4 times this week",
    "suggested_fix": "Consider compiling to a script for efficiency",
    "fix_type": "trajectory_compilation"
}
```

## The Compilation Process

### Step 1: Extract the Trajectory

From episodic memory, extract the successful execution pattern:

```bash
# Find successful runs of the target procedure
grep -l '"procedure": "add-api-endpoint"' harness/memory/episodes/*.jsonl | \
  xargs grep -h '"outcome": "success"' | head -5
```

Analyze:
- What files were created/modified?
- What was the order of operations?
- What validations were run?
- What variables differed between runs?

### Step 2: Identify Parameters

Compare multiple successful runs to identify:

| Element | Fixed or Variable? | Parameter Name |
|---------|-------------------|----------------|
| Target directory | Fixed | N/A (always `internal/`) |
| Package name | Variable | `PKG_NAME` |
| Type definitions | Variable | `TYPE_NAME` |
| Handler path | Derived | `api/handlers/${PKG_NAME}.go` |

### Step 3: Create the Script Template

Write a script that implements the compiled trajectory:

```bash
#!/bin/bash
# scripts/compiled/add-endpoint.sh
# Compiled from 5 successful "add-api-endpoint" trajectories
# Source: harness/memory/procedures/add-api-endpoint.json

set -e

# Parameters
ENDPOINT_NAME="${1:?Usage: add-endpoint.sh <endpoint-name>}"
PKG_NAME=$(echo "$ENDPOINT_NAME" | tr '[:upper:]' '[:lower:]' | tr '-' '_')

# Step 1: Create types
cat > "internal/types/${PKG_NAME}.go" << 'EOF'
package types

type ${ENDPOINT_NAME}Request struct {
    // TODO: Add request fields
}

type ${ENDPOINT_NAME}Response struct {
    // TODO: Add response fields
}
EOF

# Step 2: Create service interface
# ... (continues with compiled steps)

# Final validation
make lint-arch
go build ./...
go test ./internal/...

echo "✅ Endpoint scaffolding complete. Files created:"
echo "   internal/types/${PKG_NAME}.go"
echo "   internal/core/${PKG_NAME}.go"
echo "   api/handlers/${PKG_NAME}.go"
echo "   internal/core/${PKG_NAME}_test.go"
```

### Step 4: Add to Makefile / Quick Commands

```makefile
# Makefile addition
add-endpoint:
	@scripts/compiled/add-endpoint.sh $(NAME)
```

### Step 5: Update AGENTS.md

Add the new command to the quick reference:

```markdown
## Quick Commands (Compiled Trajectories)

| Command | Description | Source |
|---------|-------------|--------|
| `make add-endpoint NAME=foo` | Scaffold new API endpoint | Compiled from 5 successful runs |
| `make new-design-doc COMPONENT=bar` | Create design doc from template | Compiled from 4 successful runs |
```

## Validation and Safety

Compiled policies should still validate their output:

### Pre-Flight Checks
- Verify the target doesn't already exist
- Confirm parameters are valid (naming conventions, etc.)
- Check layer placement is correct

### Post-Flight Validation
- Run `make lint-arch` to verify no layer violations
- Run `go build ./...` to verify compilation
- Run relevant tests

### Fallback to Agent

If the compiled script fails or encounters an unexpected situation:

```bash
# In the compiled script
if ! make lint-arch 2>/dev/null; then
    echo "⚠️ Compiled policy hit an edge case. Falling back to agent execution."
    echo "Please run: implement endpoint $ENDPOINT_NAME"
    exit 1
fi
```

## Tracking Compiled Policies

Maintain a registry of compiled policies:

```json
// harness/compiled/registry.json
{
    "policies": [
        {
            "name": "add-endpoint",
            "command": "make add-endpoint NAME=<name>",
            "compiled_from": "harness/memory/procedures/add-api-endpoint.json",
            "trajectory_count": 5,
            "success_rate_before_compilation": 0.90,
            "executions_since_compilation": 12,
            "failures_since_compilation": 0,
            "estimated_savings": "$2.40 per execution"
        }
    ],
    "total_estimated_savings": "$28.80"
}
```

## When to Re-Compile

Compiled policies may need updates when:

1. **Architecture changes**: Layer structure updates may invalidate paths
2. **Naming convention changes**: New conventions need new templates
3. **Tool upgrades**: New linter rules may reject previously valid output
4. **Failure rate increases**: If the compiled policy starts failing, re-analyze trajectories

Set a monitoring threshold:

```
If failures_since_compilation / executions_since_compilation > 0.1:
    Flag for re-compilation review
```

## The Ultimate Goal: Self-Improving Harness

The trajectory compilation capability closes the loop on harness self-improvement:

```
Agent uses harness → Harness captures trajectories →
Patterns emerge → Patterns compiled to scripts →
Agent uses scripts → Faster, cheaper, more consistent →
Agent focuses on novel tasks → More trajectory data →
More patterns compiled → Virtuous cycle
```

The harness becomes a ratchet: every successful pattern that can be compiled becomes permanent infrastructure, freeing the agent to tackle genuinely novel problems.
