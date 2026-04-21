---
name: harness-executor
description: Execute development tasks autonomously with self-validation. Auto-bootstraps harness via harness-creator if missing. Use when the user asks to implement features, fix bugs, refactor code, execute plans, or make any code change in an existing or new codebase.
---

# Harness Executor

Execute development tasks autonomously: setup → plan → execute → validate → verify → record → present.

> **Core Philosophy**: "The Agent Harness is the Operating System. The LLM is just the CPU." Verify your changes mechanically through automated checks, not hope.

> **Architecture Principle**: **Coordinator manages state, Subagent executes code.** The coordinator spawns subagents for code changes and verification. The subagent never calls task_state.py.

## Script Execution

This skill bundles helper scripts in its `scripts/` subdirectory. Before running any script, determine this skill's installation directory from the path of this SKILL.md file, and set:

```bash
SKILL_DIR="<directory containing this SKILL.md>"
```

Then call scripts as: `python3 "$SKILL_DIR/scripts/xxx.py"`. All bash examples below assume `SKILL_DIR` has been set this way.

---

## Execution Flow

Every task follows the same seven steps. **No exceptions, no shortcuts.**

```
COORDINATOR
═══════════════════════════════════════════

 1. SETUP      bootstrap → check interrupted → query memory → load context
 2. PLAN       scope the work → init state → (multi-phase: plan file + user approval)
 3. EXECUTE    spawn executor subagent → make code changes → checkpoint
 4. VALIDATE   static validation (build, lint, test)
 5. VERIFY     spawn verifier subagent → functional verification (MANDATORY)
 6. RECORD     task_state.py complete → episodic memory → AutoHarness
 7. PRESENT    results summary to user

═══════════════════════════════════════════
```

> ⚠️ **CRITICAL**: Steps 4 and 5 are BOTH mandatory for ALL tasks. Static validation proves code compiles. Functional verification proves code *works*. Never skip Step 5.

---

## Step 1: Setup

### 1.1 Bootstrap Harness

```bash
test -f AGENTS.md && echo "HARNESS_EXISTS=true" || echo "HARNESS_EXISTS=false"
```

If `HARNESS_EXISTS=false`: invoke `Skill(skill="harness-creator")` first.

### 1.2 Check Interrupted Tasks

```bash
python3 "$SKILL_DIR/scripts/task_state.py" list
```

If an `in_progress` task matches the current request → Resume Protocol (see below).

### 1.3 Query Memory

```bash
if [ -d "harness/memory" ]; then
  python3 "$SKILL_DIR/scripts/memory_query.py" search "<relevant-keyword>" --json 2>/dev/null || echo '{"results": []}'
else
  echo "No memory store yet — skipping"
fi
```

### 1.4 Load Context

Read: `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT.md`.

Extract: build command, test command, lint command, validation script path.

---

## Step 2: Plan

**All tasks**: Identify files to modify/create, decide the approach, initialize task state.

**Multi-phase tasks** (touching 3+ files or requiring sequential changes): Write a plan file, get user approval.

### Initialize Task State (all tasks)

```bash
TASK_ID=$(python3 "$SKILL_DIR/scripts/task_state.py" init "<task-name>" \
  --phases <N> \
  --description "<description>" \
  --plan-path "docs/exec-plans/active/YYYY-MM-DD-<slug>.md")  # optional for single-phase
echo "Task ID: $TASK_ID"
```

### Multi-Phase Plan File

```bash
mkdir -p docs/exec-plans/active
```

Write to `docs/exec-plans/active/YYYY-MM-DD-<task-slug>.md`:

```markdown
# [Task Name]

**Created**: YYYY-MM-DD

## Goal
One sentence describing what success looks like.

## Scope
- **Files to modify**: [list]
- **Files to create**: [list]

## Phases

### Phase 1: [Name]
- [ ] Step 1.1: [action]
- **Validates with**: `[command]`

### Phase 2: [Name]
- [ ] Step 2.1: [action]
- **Validates with**: `[command]`
```

### Multi-Phase User Approval

Use `AskUserQuestion` with options: **Approve** / **Approve with changes** / **Reject**.

---

## Step 3: Execute

Spawn an executor subagent to make code changes. **The coordinator never writes code directly.**

### Executor Subagent Prompt

```
Agent(
  description="Execute: [task-name]",
  prompt="""
You are a code executor. Your ONLY job is to make code changes.

## Task
[task description]

## Project Root
[absolute path]

## Files to Modify/Create
[explicit list]

## Validation Command
After making changes, run:
```
[project-specific command, e.g., go build ./... && make lint-arch]
```

## Prior Lessons
[paste lessons from memory_query, or "none"]

## Output Format
Return this JSON block at the end of your response:
```json
{
  "status": "success | failed | blocked",
  "summary": "one paragraph describing what you did",
  "files_changed": ["file1.go", "file2.go"],
  "files_created": ["new_file.go"],
  "validation_result": "pass | fail",
  "validation_output": "relevant output if failed",
  "lessons": ["any insights worth remembering"],
  "blockers": ["if blocked, describe what's stopping you"]
}
```

## Rules
- Focus ONLY on making code changes
- Do NOT manage task state or checkpoints — the coordinator handles that
- If validation fails, fix and retry (max 3 attempts)
- If blocked, return with status "blocked"
"""
)
```

### Checkpoint (after successful executor return)

```bash
python3 "$SKILL_DIR/scripts/task_state.py" checkpoint \
  --task-id "$TASK_ID" \
  --phase <N> \
  --summary "<phase summary from subagent>" \
  --files-changed <file1> <file2> \
  --decisions '["key decisions from subagent lessons"]'
```

### Failure Handling

| Subagent Status | Action |
|---|---|
| `success` | Continue to Step 4 |
| `failed` | Retry with additional context (max 2 retries) |
| `blocked` | Escalate to user |

---

## Step 4: Validate (Static)

Run static validation to ensure code compiles and passes lints/tests.

```bash
if [ -f "scripts/validate.py" ]; then
  python3 scripts/validate.py .
else
  # Use commands from docs/DEVELOPMENT.md
  <build-command> && <lint-command> && <test-command>
fi
```

If static validation fails:
1. Analyze error output
2. Return to Step 3 with fix instructions (spawn executor again)
3. Max 2 retries, then escalate to user

---

## Step 5: Verify (Functional) — MANDATORY

> ⚠️ **This step is MANDATORY for ALL tasks.** Do NOT skip to Step 6 without completing verification.

Static checks only prove code compiles. Functional verification proves code *works* — by starting the actual application, making real HTTP requests, and verifying observable behavior.

### 5.1 Design Verification Scenarios

Based on what changed, design 1-3 task-specific scenarios (see `references/scenario-design-guide.md`):

| Change Type | Scenarios to Design |
|---|---|
| New endpoint | Create success, validation error, persistence check |
| Modified endpoint | New behavior works, old behavior unchanged |
| New validation | Valid input accepted, invalid input rejected |
| Permission change | Authorized user succeeds, unauthorized user rejected |
| Bug fix | The specific bug is fixed |

### 5.2 Spawn Verifier Subagent

```
Agent(
    description="Functional Verifier: [task-name]",
    prompt="""
You are a Functional Verifier agent. Read the verifier guide at:
$SKILL_DIR/agents/verifier.md

## Task Context
- Project root: [absolute path]
- Task description: [what was implemented]
- Files changed/created: [list]

## Environment Context (from environment.json if exists)
- Startup: [command], Readiness: [check config]
- Services: [databases, caches], Env Vars: [required vars]

## Scenarios to Verify
[your designed scenarios as JSON array]

## Your Responsibilities
1. Start the application server
2. Execute ALL scenarios
3. For each: verify behavior AND side effects with real HTTP requests
4. Stop the server cleanly
5. Save results to: harness/trace/verification-report.json

## Output Requirements
Your verification-report.json MUST include:
- server.started: true (prove you started the app)
- At least one scenario with request/response evidence
"""
)
```

### 5.3 Handle Verifier Result

| Result | Action |
|---|---|
| `pass` | Continue to Step 6 |
| `partial` | Fix failing scenarios related to task, log unrelated as warnings |
| `fail` | Return to Step 3 with fix instructions, max 2 retries, then escalate |

### 5.4 If Verification Cannot Run

If the application cannot be started (no server, library project, missing infrastructure), write a skip report:

```bash
mkdir -p harness/trace
cat > harness/trace/verification-report.json << 'EOF'
{
  "overall_status": "skip",
  "skip_reason": "[explain why: e.g., 'Library project with no runnable server', 'Missing required database']",
  "server": {"started": false},
  "task_specific_scenarios": [],
  "summary": {"task_specific_total": 0, "task_specific_passed": 0, "pass_rate": 0}
}
EOF
```

---

## Step 6: Record & Complete

### Complete Task

```bash
python3 "$SKILL_DIR/scripts/task_state.py" complete \
  --task-id "$TASK_ID" \
  --summary "Completed: <overall summary>" \
  --files-changed file1 file2 \
  --files-created new_file \
  --validation '{"build": "pass", "lint": "pass", "test": "pass"}' \
  --lessons '["lesson1", "lesson2"]'
```

> ⚠ **Completion Gate**: `complete` checks for `harness/trace/verification-report.json`. It **rejects** if:
> - File is missing (Step 5 was skipped)
> - Report lacks `server.started` or HTTP evidence (unless `overall_status: "skip"`)

**Move plan file** (if exists):

```bash
mkdir -p docs/exec-plans/completed
mv "docs/exec-plans/active/<plan-file>.md" "docs/exec-plans/completed/" 2>/dev/null || true
```

### AutoHarness Check

```bash
TASK_COUNT=$(python3 "$SKILL_DIR/scripts/task_state.py" list --json 2>/dev/null | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(len([t for t in d if t.get('status')=='completed']))" 2>/dev/null || echo 0)

if [ "$TASK_COUNT" -ge 3 ]; then
  python3 "$SKILL_DIR/scripts/harness_critic.py" --since 7d 2>/dev/null || true
fi
```

---

## Step 7: Present Results

```
## Task Complete

### Changes Made
- Modified `path/to/file` — [what changed]
- Created `path/to/new-file` — [purpose]

### Validation Results
- Build: PASS | Lint: PASS | Test: PASS

### Verification Results
- Server started: YES
- Scenarios: [N] designed, [N] passed
- Evidence: [summary of what was verified]

### Lessons Recorded
- [aggregated lessons]

### Next Steps
1. Create PR
2. Commit to current branch
```

---

## Resume Protocol

When Step 1.2 finds an interrupted task:

```bash
python3 "$SKILL_DIR/scripts/task_state.py" show --task-id <TASK_ID> --json
```

Resume from the last successful checkpoint:
- Read `harness/tasks/<task-id>/state/context.json`
- Pass context to subagent for the next phase
- Continue the execution loop

---

## Reference Files

| File | When to Read | Contents |
|---|---|---|
| `agents/verifier.md` | Step 5.2: spawn Functional Verifier | Verifier subagent instructions, bootstrap protocol, output format |
| `references/scenario-design-guide.md` | Step 5.1: designing scenarios | Scenario design patterns and examples |
| `references/functional-verification-guide.md` | Understanding the verification flow | Static validation → Functional Verifier architecture |
| `references/environment-schema.md` | Reading environment.json | environment.json contract: startup, services, env_vars |
| `references/validation-guide.md` | Step 4: static validation | Validation order, error recovery |
| `references/state-management.md` | Task state operations | task.json/context.json/checkpoint schemas |

---

## Guardrails

These are hard constraints. Violating them causes task completion to fail.

| Guardrail | Enforced By | Consequence |
|---|---|---|
| Must spawn verifier subagent | `complete` command | Rejects without verification-report.json |
| Must have HTTP evidence | `complete` command | Rejects if report lacks request/response |
| Must start application | `complete` command | Rejects if server.started=false (unless skip) |

If you find yourself wanting to bypass these guardrails, stop and reconsider. The guardrails exist because skipping verification is the #1 cause of "it compiled but doesn't work" bugs.
