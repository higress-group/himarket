# Functional Verification Guide

This document describes **functional verification** — the intelligent verification layer that tests whether features actually work by starting the application, making real HTTP requests, and verifying observable behavior.

---

## The Two-Layer Verification Architecture

```
                    ┌──────────────┐
                    │  Functional  │  ← Verifier Subagent (MANDATORY for ALL tasks)
                    │  Verifier    │     Starts server, executes scenarios, verifies side effects
                    ├──────────────┤
                    │  Static      │  ← validate.py (deterministic)
                    │  Validate    │     Build, lint, test
                    └──────────────┘
```

| Layer | Tool | What It Checks | Blocking? | When |
|-------|------|----------------|-----------|------|
| Static | `validate.py` | Code compiles, lints pass, tests pass | Yes — must pass | Step 4 |
| Functional | Verifier Subagent | Server starts, endpoints work, data persists, side effects happen | **Yes — THE completion gate** | Step 5 |

> **Key insight:** Static validation proves code compiles. Functional verification proves code *works*. The Verifier Subagent is the single source of truth for runtime behavior — it starts the application, makes real HTTP requests, and reports with evidence.

---

## When Functional Verification Runs

Functional verification runs for **ALL tasks**. There are no exceptions. The number of scenarios scales with the scope of the change:

| Change Scope | Scenarios |
|--------------|-----------|
| Single-file, narrow change | 1-2 focused scenarios |
| Multi-file feature | 2-5 scenarios covering happy path, error path, side effects |
| Cross-layer or architectural | 2-5 scenarios plus regression checks |

---

## Step 5 Flow

```
STEP 5: VERIFY (Functional)
═══════════════════════════════════════════════════

5.1  Design verification scenarios (Coordinator)
      │  Based on task_description, files_changed, environment.json
      ↓

5.2  Spawn Functional Verifier Subagent
      │ Start server
      │ Wait for readiness (health endpoint / TCP / log pattern)
      │ Execute ALL scenarios
      │ Verify behavior + side effects
      │ Stop server
      │ Return verification-report.json
      ↓

5.3  Validate report (guardrail check)
      ⚠ REJECTS if verification-report.json missing
      ⚠ REJECTS if report lacks HTTP request/response evidence

─── Steps 6 & 7 (Record & Present) handled by Coordinator ───
6.   task_state.py complete → Move plan → AutoHarness analysis
7.   Present results to user
```

---

## Designing Task-Specific Scenarios

**This is the Coordinator's job in Step 5.1.** See `references/scenario-design-guide.md` for full details.

Quick checklist:
1. Read `files_changed` and `files_created` from subagent results
2. Identify what behavior changed (new endpoint, modified logic, new validation, etc.)
3. Design scenarios that verify the **actual changes**
4. Use `environment.json` to know what databases/services are available
5. Include `why` field explaining business reason for each scenario

Example:

```json
{
  "task_specific_scenarios": [
    {
      "name": "verify_user_registration_persists",
      "description": "Verify POST /api/register creates user in database",
      "requires": ["postgres"],
      "steps_hint": [
        "POST /api/register with valid data",
        "Assert 201 response",
        "GET /api/users/{id} to verify persistence"
      ],
      "why": "Core success path - user must be persisted",
      "priority": "high"
    }
  ]
}
```

---

## Spawning the Verifier Subagent

The coordinator spawns the Verifier with a fully self-contained prompt:

```python
env_config = load_json("harness/config/environment.json") if exists else {}
predefined = env_config.get("functional_scenarios", [])
task_scenarios = [...] # Designed in Step 5.1

Agent(
    description=f"Functional Verifier: {task_name}",
    prompt=f"""
You are a Functional Verifier agent. Read the verifier guide at:
{SKILL_DIR}/agents/verifier.md

## Task Context
- Project root: {PROJECT_ROOT}
- Task description: {task_description}
- Files changed: {files_changed}
- Files created: {files_created}

## Environment Context
{json.dumps(env_config, indent=2)}

## Application
- Start command: {env_config.get("runtime", {}).get("dev_command", "<from DEVELOPMENT.md>")}
- Test environment: {json.dumps(env_config.get("test_environment", {}).get("env_vars", {}))}

## Scenarios to Verify

### Pre-defined (from environment.json):
{json.dumps(predefined, indent=2) if predefined else "None"}

### Task-Specific (designed by Coordinator):
{json.dumps(task_scenarios, indent=2)}

## Your Responsibilities
1. Start the application server
2. Execute ALL task-specific scenarios (priority 1)
3. Execute ALL pre-defined scenarios (priority 2)
4. For each: verify behavior AND side effects with real HTTP requests
5. Stop the server cleanly
6. Report with evidence

## Output
Save results to: harness/trace/verification-report.json
"""
)
```

---

## Verifier Output

The verifier produces `harness/trace/verification-report.json`:

```json
{
  "overall_status": "pass | partial | fail",
  "server": {
    "started": true,
    "ready_in_seconds": 2.3,
    "stopped_cleanly": true
  },
  "task_specific_scenarios": [
    {
      "name": "verify_user_registration_persists",
      "source": "coordinator_designed",
      "why": "Core success path - user must be persisted",
      "status": "pass",
      "steps": [...],
      "evidence": "User created and retrievable by ID",
      "duration_seconds": 1.5
    }
  ],
  "predefined_scenarios": [
    {
      "name": "user_auth_flow",
      "source": "environment_json",
      "status": "pass",
      "relevant_to_task": true,
      "steps": [...],
      "evidence": "...",
      "duration_seconds": 2.0
    }
  ],
  "additional_checks": [...],
  "summary": {
    "task_specific_total": 3,
    "task_specific_passed": 3,
    "predefined_total": 2,
    "predefined_passed": 2,
    "pass_rate": 1.0
  },
  "timing": {
    "total_seconds": 8.5
  }
}
```

---

## Interpreting Results

### Overall Status

| Status | Meaning | Action |
|--------|---------|--------|
| `pass` | All scenarios passed | Proceed with completion |
| `partial` | Some scenarios passed, some failed or skipped | Review failures, decide if blocking |
| `fail` | Critical (task-specific) scenarios failed | Fix issues, re-run |

### Priority-Based Failure Handling

| Scenario Source | On Failure |
|-----------------|------------|
| `task_specific_scenarios` | **BLOCKING** — must fix and retry |
| `predefined_scenarios` (relevant to task) | Likely blocking — fix unless clearly unrelated |
| `predefined_scenarios` (unrelated) | Warning — log and proceed |

---

## What Functional Verification Catches

A change to the registration endpoint:

| Check Type | What Static Validation Sees | What Functional Verifier Sees |
|------------|---------------------------|------------------------------|
| Compilation | Code compiles ✓ | — |
| Status | — | POST /register → 201 ✓ |
| Persistence | — | GET /users/{id} → 404 ✗ (not saved!) |
| Auth Flow | — | POST /login → 401 ✗ (password hash wrong) |
| Side Effects | — | Email confirmation not sent ✗ |

This is why functional verification exists — it catches **semantic bugs** that pass static checks.

---

## Backward Compatibility

- `environment.json` is **optional** — projects without it still get task-specific verification
- Pre-defined `functional_scenarios[]` supplement task-specific scenarios, they don't replace them
- The standalone `verify.py` script remains available as an independent tool for quick manual smoke checks, but is not part of the automated skill flow
