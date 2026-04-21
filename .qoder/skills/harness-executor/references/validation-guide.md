# Validation Guide

Detailed guide on the self-validation loop, failure handling, and trajectory capture.

## Validation Pipeline

Run checks in order. If any required check fails, fix and restart from that check.

```bash
# Full validation (auto-detects project type)
python3 scripts/validate.py .

# Specific steps only
python3 scripts/validate.py . --steps build lint-arch test

# JSON output for programmatic use
python3 scripts/validate.py . --json --output report.json

# Continue past failures (useful for getting a full picture)
python3 scripts/validate.py . --no-stop-on-failure
```

## Check Order and Rationale

**Check 1: Build** — Code must compile/parse. Fix compilation errors first — nothing else matters until this passes.

**Check 2: Architecture Lint** — Structural linters: dependency direction, interface compliance, quality rules. Well-designed harness linters have agent-actionable error messages — read the full message, it tells you WHAT is wrong AND HOW to fix it.

**Check 3: Tests** — All existing tests must pass. If your change breaks a test, either your change has a bug (fix it) or the test needs updating (update it, but understand why it exists first).

**Check 4: Quality Score** (optional) — If available, check your change doesn't decrease quality.

**Check 5: Eval Tasks** (optional) — If the project has eval datasets covering your change, run them.

**Check 6: Performance Targets** (optional) — If the task has performance requirements, verify with actual measurements.

## Commands by Language

Always prefer commands from `docs/DEVELOPMENT.md`. If unavailable, common defaults:

| Step | Go | TypeScript | Python |
|------|-----|-----------|--------|
| Build | `go build ./...` | `npm run build` | `ruff check .` |
| Lint | `make lint-arch` | `npm run lint:arch` | `python scripts/lint_deps.py src/` |
| Test | `go test ./...` | `npm test` | `pytest` |

### Custom Commands

If the project uses non-standard tools (pnpm, yarn, poetry, etc.), create `harness/config/validate.json`:

```json
{
  "steps": [
    {"name": "build", "command": "pnpm build", "required": true, "timeout": 300},
    {"name": "lint-arch", "command": "pnpm lint:arch", "required": true},
    {"name": "test", "command": "pnpm test", "required": true, "timeout": 600}
  ]
}
```

`validate.py` reads this file first; hardcoded defaults are only used as fallback.

## Handling Failures

### The 3-Retry Rule

If stuck in a loop (3+ iterations on the same failure):

1. **Re-read relevant documentation** — you may be misunderstanding a constraint
2. **Check for known issues** — is the linter/test itself buggy?
3. **Check episodic memory** — `harness/memory/episodes/` may have similar past failures
4. **Escalate** — report the blocker to the user with what you've tried

### Escalation Signals

| Signal | Likely Cause | Action |
|--------|-------------|--------|
| 3+ failures on same rule | Misunderstanding architecture | Re-read ARCHITECTURE.md |
| Test passing then failing | Regression introduced | Diff your changes carefully |
| Unclear requirements | Ambiguous spec | Ask for clarification |
| Conflicting constraints | Design tension | Report conflict, propose alternatives |

Never silently ignore failing checks or modify linter configuration to work around issues.

## Trajectory Capture

Every validation loop produces useful data. After significant failures or recoveries, log to episodic memory:

```json
{
  "event": "lint_failure_resolved",
  "timestamp": "2026-03-23T10:30:00Z",
  "details": "Imported core/config from types/ — layer violation",
  "resolution": "Moved config-dependent code to core/",
  "lesson": "Layer 0 cannot import Layer 2"
}
```

This builds institutional knowledge. Future agents can avoid the same mistakes.

## Error Recovery Patterns

**Build failures:** Read error messages carefully. Check if you're importing from the wrong layer. Verify function signatures match interfaces.

**Lint failures:** In a well-designed harness, lint errors are **instructions**. Read the full message. Common issues:
- Dependency violation → restructure imports to respect layer hierarchy
- Quality violation → use structured logging, respect file size limits
- Template violation → check tag pairs

If the error message doesn't explain how to fix it, that's a harness gap — note it for `harness-creator` to address later.

**Test failures:** Read the test name and assertion to understand intent. Run the specific test with verbose output to debug.
