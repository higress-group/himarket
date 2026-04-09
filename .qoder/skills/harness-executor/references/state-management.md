# State Management

How to track task state, checkpoints, and episodic memory.

## Why State Management Matters

Three concrete reasons:
1. If context degrades at tool call 60+, re-reading state files restores your position
2. If the task gets interrupted, you (or another agent) can resume from the last checkpoint
3. Decision logs prevent contradicting earlier architectural choices

All tasks use the same state management flow. There are no shortcuts.

## Task Directory Structure

Each task gets its own isolated directory under `harness/tasks/`:

```
harness/
└── tasks/
    ├── current -> plugin-architecture-20260324-1600/  (symlink to active task)
    ├── plugin-architecture-20260324-1600/
    │   ├── state/
    │   │   ├── task.json        # Task metadata and status
    │   │   ├── context.json     # Current execution context
    │   │   ├── decisions.json   # Decision log (optional)
    │   │   └── result.json      # Final result (written on completion)
    │   └── checkpoints/
    │       ├── phase-1.json
    │       └── phase-2.json
    └── event-refactor-20260323-0900/   (previous task)
        └── ...
```

## Using task_state.py

The `scripts/task_state.py` CLI handles all state management. Prefer it over manual bash commands.

### Task Lifecycle

```bash
# Step 2: Initialize
python3 scripts/task_state.py init "plugin-architecture" \
  --phases 4 \
  --description "Redesign plugin loading system" \
  --plan-path "docs/exec-plans/active/2026-03-24-plugin-architecture.md"

# Step 3: Checkpoint after each phase (optional for single-phase tasks)
python3 scripts/task_state.py checkpoint \
  --phase 2 \
  --summary "Types defined, interfaces implemented" \
  --decisions '["Put EventBus in L0 for cross-layer access"]' \
  --files-changed internal/event/event.go internal/message/queue.go

# Step 6: Complete (requires verification-report.json)
python3 scripts/task_state.py complete \
  --summary "Plugin architecture implemented with lazy loading" \
  --files-changed internal/plugin/loader.go internal/plugin/registry.go \
  --files-created internal/plugin/types.go \
  --validation '{"build": "pass", "lint": "pass", "test": "pass"}' \
  --lessons '["Plugin interfaces must be in L0 to avoid circular deps"]'
```

> ⚠ The `complete` command enforces the verification gate — it will reject if
> `harness/trace/verification-report.json` is missing or lacks HTTP evidence.

### Query Commands

```bash
# Show task details
python3 scripts/task_state.py show --task-id <TASK_ID>
python3 scripts/task_state.py show --json  # current task, JSON output

# List all tasks
python3 scripts/task_state.py list
python3 scripts/task_state.py list --json
```

## File Schemas

### task.json

```json
{
  "task_id": "plugin-architecture-20260324-1600",
  "task": "Redesign plugin loading system",
  "started_at": "2026-03-24T16:00:00+08:00",
  "plan_path": "docs/exec-plans/active/2026-03-24-plugin-architecture.md",
  "phase": 2,
  "total_phases": 4,
  "status": "in_progress"
}
```

Status values: `in_progress`, `completed`, `failed`, `blocked`.

### context.json

```json
{
  "completed": [
    "Phase 1: types defined in internal/event/event.go",
    "Phase 2: PriorityQueue upgraded to heap-based"
  ],
  "current": "Phase 3: Implementing EventBridge adapter",
  "remaining": ["Phase 4: Migrate TUI", "Phase 5: Validation"],
  "key_decisions": [
    "EventBus placed in L0 (internal/event/) — no internal imports",
    "EventSink.OnTurnComplete changed to primitive types to avoid L1 dependency"
  ],
  "files_modified": ["internal/event/event.go", "internal/message/queue.go"],
  "files_created": ["internal/event/bridge.go"],
  "validation_command": "go build ./... && make lint-arch && go test -race ./..."
}
```

### decisions.json (optional, for significant architectural choices)

```json
[
  {
    "decision": "Put EventBus in L0 instead of L1",
    "reasoning": "All layers need to publish/subscribe; L0 has no internal deps",
    "alternatives": ["Put in provider (L1) — rejected: TUI couldn't use it"]
  }
]
```

### result.json (written on completion)

```json
{
  "status": "success",
  "files_changed": ["path/to/file"],
  "files_created": ["path/to/new-file"],
  "validation": {"build": "pass", "lint": "pass", "test": "pass"},
  "tool_calls_used": 42,
  "blockers": [],
  "summary": "Implemented plugin loading with lazy initialization"
}
```

## Episodic Memory

Lessons are recorded to `harness/memory/episodes/YYYY-MM-DD.jsonl`:

```json
{
  "task": "Refactor message queue and event system",
  "task_id": "msg-queue-refactor-20260323-0900",
  "outcome": "success",
  "lessons": [
    "EventSink interface must use primitive types for L0 compatibility",
    "TUI can subscribe to EventBus via async handler to avoid blocking"
  ],
  "timestamp": "2026-03-23T10:30:00+08:00"
}
```

## Resuming an Interrupted Task

```bash
# Check for active task
python3 scripts/task_state.py list

# Show full state
python3 scripts/task_state.py show --task-id <TASK_ID> --json
```

The coordinator reads the state and passes context to a fresh subagent, which picks up where the previous one left off.
