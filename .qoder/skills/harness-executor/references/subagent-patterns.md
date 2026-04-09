# Subagent Delegation Patterns

How to delegate task execution to subagents to prevent context blowup.

## Why Subagents

Long tasks accumulate context → context compression → lost critical information → confused agent. Subagents solve this by giving each task a fresh context. When the subagent completes, its context is released — the coordinator only retains a summary.

The most common failure mode in practice: the coordinator "just quickly" starts editing code directly instead of spawning a subagent. This always escalates — one edit becomes five, five becomes twenty, and by then the context is consumed and the coordinator has lost the big picture. The fix is simple: the coordinator spawns a subagent for code changes and NEVER touches Edit/Write tools on source files.

```
Main Agent (Coordinator)          Subagent (Executor)
─────────────────────────         ─────────────────────
Small context (~2K tokens)        Fresh context per task
Plans, approves, collects         Executes, validates
Never writes code                 Writes all code
Survives across tasks             Released after task
```

## Spawning an Executor

The subagent prompt must be self-contained — it starts with zero context about the project. Include everything it needs in the prompt.

### Standard Subagent (Medium Tasks)

```
Agent(
  description="Execute: [short-task-name]",
  prompt="""
You are a Harness Executor agent. Execute this development task autonomously.

## Task
[task description from the plan]

## Project Root
[absolute path to project root]

## Context Files — Read These First
1. AGENTS.md — navigation map (~100 lines)
2. docs/ARCHITECTURE.md — layer hierarchy, forbidden dependencies
3. docs/DEVELOPMENT.md — build/test/lint commands
4. [any task-specific docs, e.g. docs/design-docs/foo.md]

## Approved Plan
[paste the plan summary or path to plan file]

## Your Job
1. Read the context files listed above
2. Execute code changes per the plan
3. Before creating files in new locations or adding cross-package imports:
   - Run: python3 scripts/verify_action.py --action "your proposed action" --suggest
   - If VALID → proceed
   - If INVALID → read the fix_suggestions and choose an alternative
   - Report verify_action results in your final summary
4. Run validation after changes: python3 scripts/validate.py .
   (Always use the unified pipeline, not raw build/test commands)
5. If validation fails, fix and re-validate (max 3 retries per failure, then report)
6. Return a JSON result block with status, summary, files_changed, files_created, validation_result, lessons, and blockers

NOTE: Do NOT call task_state.py — the coordinator handles all state management (checkpoints, completion).

## Rules
- Do NOT ask for user input — execute autonomously
- Respect the layer hierarchy from ARCHITECTURE.md
- Follow naming conventions from project docs
- Write tests alongside code
- If stuck after 3 retries on the same failure, report blockers and return
"""
)
```

### Worktree-Isolated Subagent (Complex Tasks)

Same prompt, but with `isolation="worktree"` if the environment supports it:

```
Agent(
  description="Execute: [short-task-name]",
  isolation="worktree",    # ← Preferred if available, omit if unsupported
  prompt="... (same as above) ..."
)
```

Worktree isolation gives the subagent its own copy of the repo, preventing partial changes from affecting the main branch if the task fails. Not all environments support this — if unavailable, use a standard subagent instead.

## Multi-Task Patterns

### Independence Check

Before deciding parallel vs sequential:
1. Compare `files_to_modify` lists — any overlap → sequential
2. Check if tasks touch the same architectural layer with mutual dependencies → sequential
3. If truly independent → parallel

### Parallel Execution (independent tasks)

```
# Spawn all in the same message — they run concurrently
Agent(description="Execute: add-logging", prompt="...", run_in_background=True)
Agent(description="Execute: fix-error-msgs", prompt="...", run_in_background=True)
Agent(description="Execute: update-readme", prompt="...", run_in_background=True)
```

Each gets its own context, own state directory, own validation. Main agent stays lean.

### Sequential Execution (dependent tasks)

```
# Task A first
result_a = Agent(description="Execute: define-types", prompt="...")
# Read result, then Task B
result_b = Agent(description="Execute: implement-handler", prompt="...")
```

### Conflicting Tasks (touch same files)

```
# Use worktree isolation — each gets its own repo copy
Agent(description="Execute: refactor-auth", isolation="worktree", prompt="...", run_in_background=True)
Agent(description="Execute: add-oauth", isolation="worktree", prompt="...", run_in_background=True)
# Merge results after both complete
```

## Collecting Results

After each subagent completes, the **coordinator** reads its result from the JSON block in the subagent's response. The coordinator then checkpoints the state:

```bash
python3 scripts/task_state.py checkpoint --task-id ${TASK_ID} --phase N --summary "subagent summary"
```

The coordinator's context now contains only the **summary**, not the 50+ tool calls of execution detail.

**If success** → proceed to completion.
**If failed or blocked** → decide:
- Fixable? → spawn a new subagent with the blocker context (fresh context again)
- Needs user input? → escalate to user with the blocker details
- Wrong approach? → go back to planning (re-plan)

## Action Verification Loop (AutoHarness Pattern)

A key insight from [AutoHarness](https://arxiv.org/abs/2603.03329): 78% of agent failures come from "illegal moves" — actions that violate the environment's rules. Instead of only catching these at validation time (post-execution), the Propose-Verify-Refine pattern catches them before execution, saving context and time.

### Why Pre-Verification Matters

| Approach | When Error Caught | Context Cost | Recovery Effort |
|----------|------------------|-------------|-----------------|
| Post-execution only | After code is written | High (undo changes) | High (rewrite) |
| **Pre-verification** | Before code is written | Low (re-plan) | Low (choose alternative) |

The difference is dramatic for medium/complex tasks: a layer violation caught before writing 50 lines of code costs ~2 tool calls to fix. The same violation caught after writing requires ~10 tool calls to undo and rewrite.

### The Verify-Before-Execute Pattern

Subagents should verify significant actions before executing them:

```python
# Before creating a file in a new location
result = subprocess.run(
    ["python3", "scripts/verify_action.py", "--action", f"create file {filepath}", "--json"],
    capture_output=True, text=True
)
verification = json.loads(result.stdout)

if not verification["valid"]:
    # Don't create the file — re-plan using the rejection reason
    # The rejection_reason and fix_suggestions tell you exactly what to do instead
    pass
```

### When to Verify

Not every action needs pre-verification — that would be overhead without benefit. Verify when:

| Action | Verify? | Why |
|--------|---------|-----|
| Creating files in `internal/` or `pkg/` | **Yes** | Layer placement matters |
| Adding new imports | **Yes** | Layer violations are the #1 failure |
| Modifying `AGENTS.md`, `ARCHITECTURE.md` | **Yes** | Protected/critical files |
| Editing an existing function body | No | Already in the right layer |
| Adding a test file | No | Tests don't have layer constraints |
| Renaming/moving files across directories | **Yes** | May cross layer boundaries |

### Integrating with the Standard Subagent Prompt

Add this to the subagent's "Your Job" section for medium+ tasks:

```markdown
## Action Verification (for structural changes)
Before creating files in new locations or adding cross-package imports:
1. Run: python3 scripts/verify_action.py --action "your proposed action" --suggest
2. If VALID → proceed
3. If INVALID → read the fix_suggestions and choose an alternative approach
4. Log the rejection to harness/trace/failures/ for future harness improvement
```

### Failure Logging for Critic Analysis

When an action is rejected (either by pre-verification or post-validation), log it:

```json
{
    "timestamp": "2026-03-24T10:30:00Z",
    "failure_type": "verify",
    "error_message": "Layer violation: L0 (internal/types) cannot import L2 (internal/core)",
    "file_path": "internal/types/user.go",
    "attempted_fix": "Moved dependency to constructor parameter",
    "outcome": "fixed"
}
```

Save to `harness/trace/failures/YYYY-MM-DD.jsonl` (one event per line). The Harness Critic script (`scripts/harness_critic.py`) periodically analyzes these logs to suggest harness improvements.

### Critic → Refiner Pipeline

After a batch of task executions, the Critic analyzes failure patterns:

```bash
# Analyze recent failures
python3 scripts/harness_critic.py --since 24h --json --output harness/trace/critic-report.json

# The report contains:
# - Detected patterns (repeated layer violations, opaque errors, etc.)
# - Root cause hypotheses
# - Suggested fixes (update layer map, improve error messages, add rules)
# - Priority ordering (P0-P3)
```

The coordinator can use this report to decide whether to invoke `harness-creator` for harness improvements before continuing with more tasks.

---

## Context Management Inside Subagents

The subagent executor should follow this rhythm:

- **Every 10 tool calls**: Quick mental checkpoint — am I still on track?
- **Every 20 tool calls**: Write a brief progress note in the response (the coordinator will extract this for checkpointing)
- **Every phase completion**: Return the JSON result block

The coordinator reads the subagent's response and calls `task_state.py checkpoint` — the subagent itself never manages state.

## Example: Three-Task Workflow

```
User: "1) Add logging to auth, 2) Fix API error messages, 3) Update README"
```

**Coordinator analysis:**
- Task 1: 3-4 files, auth module
- Task 2: 5 files, API layer
- Task 3: 1 file, README

**Independence check:** auth vs API → different layers ✓, README → docs only ✓

**Execution:**
```
# All independent → parallelize
Agent(description="Execute: add-auth-logging", ..., run_in_background=True)
Agent(description="Execute: fix-api-errors", ..., run_in_background=True)
Agent(description="Execute: update-readme", ..., run_in_background=True)
```

**Result:** Each subagent uses 20-40 tool calls in isolated context, coordinator uses ~10 total.
