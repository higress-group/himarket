# Harness State Audit Agent

You are auditing the existing harness infrastructure of a codebase to identify gaps and issues.

## Your Task

Produce a comprehensive audit report showing what exists, what's missing, and what's broken.

## Audit Dimensions

### 1. Documentation (Weight: 25%)

| Check | How | Pass Criteria |
|-------|-----|---------------|
| AGENTS.md exists | `test -f AGENTS.md` | File exists |
| AGENTS.md size | `wc -l AGENTS.md` | 80-120 lines |
| AGENTS.md has numbered sections | Count `##` headers | ≥ 5 sections |
| ARCHITECTURE.md exists | `test -f docs/ARCHITECTURE.md` | File exists |
| ARCHITECTURE.md has Mermaid diagrams | `grep 'mermaid' docs/ARCHITECTURE.md` | At least 1 |
| Layer claims are accurate | Cross-reference imports | No false claims |
| DEVELOPMENT.md commands work | Spot-check 2-3 commands | Commands succeed |
| Design docs exist (not just index) | `find docs/design-docs -name "*.md" ! -name "index.md"` | ≥ 2 files |
| All doc links are valid | Check `[text](path)` references | No broken links |

### 2. Linters (Weight: 20%)

| Check | How | Pass Criteria |
|-------|-----|---------------|
| lint-deps script exists | `test -f scripts/lint-deps*` | File exists |
| lint-quality script exists | `test -f scripts/lint-quality*` | File exists |
| Layer map covers all packages | Compare map vs `go list ./...` | 100% coverage |
| Can detect real violations | Create test case | Violation caught |
| Error messages are agent-actionable | Read 5 error messages | WHAT + WHY + HOW |
| `make lint-arch` passes | Run it | Exit code 0 |

### 3. Eval System (Weight: 20%)

| Check | How | Pass Criteria |
|-------|-----|---------------|
| Eval directory exists | `test -d harness/eval` | Directory exists |
| Eval datasets present | `find harness/eval/datasets -name "*.json"` | ≥ 5 tasks |
| Categories covered | Count unique categories | ≥ 3 |
| Tasks reference real files | Spot-check file paths | Valid references |
| Task freshness | Check git dates | Updated within 90 days |

### 4. Environment & Config (Weight: 15%)

| Check | How | Pass Criteria |
|-------|-----|---------------|
| environment.json exists | `test -f harness/config/environment.json` | File exists (if project has external deps) |
| Setup scripts exist | `test -f harness/scripts/setup-env.sh` | File exists |
| Scripts are executable | `test -x harness/scripts/*.sh` | Executable |
| No hardcoded secrets | `grep -r "password\|secret\|key=" harness/config/` | Uses ${VAR} references |

### 5. Integration (Weight: 10%)

| Check | How | Pass Criteria |
|-------|-----|---------------|
| Makefile has lint-arch target | `grep 'lint-arch' Makefile` | Target exists |
| Build passes | `make build` or equivalent | Exit code 0 |
| CI config exists | `test -f .github/workflows/ci.yml` | File exists |

### 6. Quality Automation (Weight: 10%)

| Check | How | Pass Criteria |
|-------|-----|---------------|
| Observability structure | `test -d harness/trace` | Directory exists |
| Memory structure | `test -d harness/memory` | Directory exists |
| Checkpointing support | `test -d harness/checkpoints` | Directory exists |

## Scoring

For each dimension, score 0-10:
- 10: All checks pass, high quality
- 7-9: Most checks pass, minor gaps
- 4-6: Some checks pass, significant gaps
- 1-3: Few checks pass, major gaps
- 0: Dimension entirely missing

## Output Format

Save results to `harness/.analysis/audit.json`:

```json
{
  "overall_score": 6.5,
  "dimensions": {
    "documentation": {"score": 7, "weight": 25, "checks_passed": 7, "checks_total": 9},
    "linters": {"score": 5, "weight": 20, "checks_passed": 3, "checks_total": 6},
    "evals": {"score": 0, "weight": 20, "checks_passed": 0, "checks_total": 5},
    "environment": {"score": 8, "weight": 15, "checks_passed": 4, "checks_total": 5},
    "integration": {"score": 9, "weight": 10, "checks_passed": 3, "checks_total": 3},
    "quality_automation": {"score": 3, "weight": 10, "checks_passed": 1, "checks_total": 3}
  },
  "gaps": [
    {"priority": "P0", "dimension": "documentation", "issue": "ARCHITECTURE.md claims 3 layers but code has 4", "fix": "Regenerate from actual imports"},
    {"priority": "P1", "dimension": "linters", "issue": "lint-deps missing 5 packages", "fix": "Add internal/cache, internal/auth to layer map"},
    {"priority": "P2", "dimension": "evals", "issue": "No eval tasks exist", "fix": "Create eval framework and initial tasks"}
  ],
  "strengths": [
    "Build passes cleanly",
    "CI properly configured",
    "Error handling is consistent"
  ]
}
```

Also write human-readable audit to `harness/.analysis/audit-summary.md`.
