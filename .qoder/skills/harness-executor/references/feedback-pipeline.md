# Feedback Pipeline: Critic → Refiner

> "Competitive advantage is no longer the prompt. It is the trajectories your Harness captures." — AutoHarness insight: The harness should learn from every failure and automatically improve itself.

This reference describes the structured feedback pipeline that transforms validation failures into harness improvements. The pipeline is inspired by the [AutoHarness paper](https://arxiv.org/abs/2603.03329), which demonstrated that automated harness refinement through environment feedback enables a smaller model + harness to outperform a larger model without one.

## The Core Loop

```
Agent executes task
    ↓
Validation catches failures (verify_action.py / validate.py)
    ↓
Failures logged to harness/trace/failures/
    ↓
Critic analyzes patterns (harness_critic.py)
    ↓
Critic produces improvement suggestions
    ↓
Refiner updates harness (manual or automated)
    ↓
Next agent benefits from improved harness
```

This is a "learned rejection sampler" — the definition of what's acceptable evolves based on observed failures.

## Phase 1: Capture

Every validation failure should be captured in a structured format. This happens automatically during task execution.

### Failure Event Schema

```json
{
    "timestamp": "2026-03-24T10:30:00Z",
    "failure_type": "lint|build|test|verify|runtime",
    "error_message": "Full error text",
    "file_path": "internal/types/user.go",
    "line_number": 15,
    "rule_id": "layer-violation",
    "attempted_fix": "What the agent tried",
    "outcome": "fixed|still_failed|escalated",
    "context": {
        "task_id": "implement-auth",
        "phase": 2,
        "agent_session": "session-abc123"
    }
}
```

### Where to Save

| Source | Save Location | Format |
|--------|---------------|--------|
| Pre-verification rejections | `harness/trace/failures/YYYY-MM-DD.jsonl` | One event per line |
| Post-validation failures | `harness/trace/failures/YYYY-MM-DD.jsonl` | One event per line |
| Episodic memory events | `harness/memory/episodes/YYYY-MM-DD.jsonl` | Full episode with events |
| Validation reports | `harness/trace/validation/YYYY-MM-DD-HHMMSS.json` | Full validate.py report |

### Capture Triggers

Subagents should log failures at these moments:

1. **verify_action.py returns INVALID** — Log the proposed action and rejection reason
2. **validate.py step fails** — Log the step, error, and any attempted fix
3. **3-retry limit reached** — Log the full failure chain before escalating
4. **Task completion with lessons** — `task_state.py complete --lessons` writes to episodic memory

## Phase 2: Critic Analysis

The Critic script (`scripts/harness_critic.py`) analyzes accumulated failures to find patterns. Run it periodically or after significant task batches.

### When to Run the Critic

| Trigger | Command | Rationale |
|---------|---------|-----------|
| After completing a batch of tasks | `python3 scripts/harness_critic.py --since 24h` | Fresh feedback while context is warm |
| Before a harness audit | `python3 scripts/harness_critic.py --json -o critic-report.json` | Data-driven audit |
| Weekly maintenance | `python3 scripts/harness_critic.py --since 7d` | Catch slow-building patterns |
| After a spike in failures | `python3 scripts/harness_critic.py --min-occurrences 3` | Identify root cause |

### Critic Output: Pattern Types

| Pattern Type | What It Means | Typical Fix |
|-------------|---------------|-------------|
| `layer_violation` | Agents keep putting code in wrong layers | Update ARCHITECTURE.md, improve lint-deps rules |
| `naming_issue` | Repeated naming convention failures | Add examples to DEVELOPMENT.md |
| `opaque_error` | Error messages don't explain how to fix | Rewrite linter messages to WHAT+WHY+HOW |
| `missing_rule` | Build failures that lint should catch earlier | Add new lint rules |
| `failure_hotspot` | Same file fails repeatedly | Review design, add component-specific docs |

### Reading the Critic Report

```json
{
    "patterns_found": [
        {
            "pattern_id": "layer-violation-1",
            "pattern_type": "layer_violation",
            "occurrence_count": 7,
            "root_cause_hypothesis": "Package 'internal/cache' not in layer map",
            "suggested_fix": "Add 'internal/cache' to lint-deps.go layer map at L1",
            "fix_type": "update_layer_map",
            "priority": "P1",
            "confidence": 0.85
        }
    ],
    "recommendations": [
        {
            "priority": "P1",
            "action": "Add 'internal/cache' to lint-deps.go layer map at L1",
            "type": "update_layer_map",
            "impact": "Affects 3 files, 7 occurrences"
        }
    ]
}
```

## Phase 3: Refiner Actions

The Refiner uses the Critic's suggestions to update harness components. This can be done by harness-creator (for significant changes) or manually (for quick fixes).

### Fix Type → Action Mapping

| Fix Type | Who Implements | How |
|----------|---------------|-----|
| `update_layer_map` | harness-creator or manual | Edit `scripts/lint-deps.go` layer map variable |
| `improve_error_message` | harness-creator or manual | Rewrite error format in lint scripts to include fix options |
| `add_rule` | harness-creator | Add new validation rule to existing linter |
| `update_docs` | harness-creator or manual | Update ARCHITECTURE.md, DEVELOPMENT.md, or design docs |

### Refiner Decision Flow

```
Read Critic report
    ↓
For each recommendation:
    ├── P0 (Critical): Fix immediately, no approval needed
    ├── P1 (High): Fix now, inform user of changes
    ├── P2 (Medium): Queue for next harness-creator improve cycle
    └── P3 (Low): Add to tech-debt-tracker.md
```

### Tracking Improvements

After applying fixes, update the feedback loop tracker:

```json
// harness/trace/improvements.jsonl (append)
{
    "timestamp": "2026-03-24T14:00:00Z",
    "triggered_by": "critic-report-20260324",
    "pattern_id": "layer-violation-1",
    "action_taken": "Added internal/cache to lint-deps.go at L1",
    "files_modified": ["scripts/lint-deps.go", "docs/ARCHITECTURE.md"],
    "expected_impact": "Prevent 7+ future layer violations involving cache package",
    "verification": "make lint-arch passes after change"
}
```

## Phase 4: Verification Loop

After the Refiner applies changes, verify the improvement actually works:

1. **Regression check**: `make lint-arch && go test ./...`
2. **Replay test**: If possible, replay the original failing scenarios to confirm they now either pass or produce clearer errors
3. **Score check**: Run `python3 scripts/detect_harness.py .` to verify the harness score didn't decrease

## Automating the Pipeline

### Integration Points

| Integration | How | Frequency |
|-------------|-----|-----------|
| Task completion hook | `task_state.py complete --lessons` auto-saves to episodic memory | Every task |
| Validation failure capture | Subagent logs to `harness/trace/failures/` on any validation fail | Every failure |
| Critic analysis | Coordinator runs `harness_critic.py` between task batches | Per batch or daily |
| Harness improvement | Coordinator decides: quick fix vs. queue for harness-creator | Based on priority |

### Directory Structure

```
harness/
├── trace/
│   ├── failures/           # Raw failure events (JSONL)
│   │   └── 2026-03-24.jsonl
│   ├── validation/         # Full validation reports
│   │   └── 2026-03-24-103000.json
│   ├── improvements.jsonl  # Applied improvements log
│   └── critic-report.json  # Latest critic analysis
└── memory/
    └── episodes/           # Episodic memory (includes failures)
        └── 2026-03-24.jsonl
```

## Thompson Sampling for Harness Improvement

The AutoHarness paper uses Thompson Sampling to balance exploration vs. exploitation when refining harness rules. While we don't implement the full tree-search algorithm, the key insight applies:

**Exploration**: Try different fix strategies for the same pattern (e.g., for a layer violation: move code to higher layer vs. inject dependency vs. define interface)

**Exploitation**: When a fix strategy has worked multiple times, prefer it for similar patterns

Track this in procedural memory:

```json
// harness/memory/procedures/fix-layer-violations.json
{
    "procedure": "Fix layer violations",
    "strategies": [
        {
            "strategy": "Dependency injection via interface",
            "success_count": 8,
            "failure_count": 1,
            "success_rate": 0.89,
            "best_for": "Cross-layer service dependencies"
        },
        {
            "strategy": "Move code to higher layer",
            "success_count": 5,
            "failure_count": 3,
            "success_rate": 0.63,
            "best_for": "Simple utility functions in wrong layer"
        }
    ]
}
```

When the Critic identifies a new layer violation, the agent can consult procedural memory to pick the strategy with the highest success rate for the specific context.
