---
name: harness-creator
description: "Design and create AI-agent infrastructure for codebases: AGENTS.md, documentation architecture (docs/), linters with actionable errors (scripts/lint-*), harness/ configs, and CI integration. Creates files directly — never writes business/application code."
---

# Harness Creator
Design and create Harness Engineering infrastructure so AI agents can work reliably in a codebase.

> **Core Philosophy**: "Intelligence without infrastructure is just a demo." The Agent Harness is the Operating System — the LLM is just the CPU. The repository becomes the single source of truth — if an agent can't see it in context, it doesn't exist.

## Unified Workflow

This skill follows a single unified workflow regardless of project state (empty, existing code, or existing harness). The core idea: **detect the gap between current state and target state, then fill it**.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Phase 1: Quick Detection + Intent Confirmation                     │
│  (5 min) What exists? What does user want?                         │
└─────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Phase 2: Parallel Analysis (spawn subagents)                       │
│  - Code Architecture Agent: imports, layers, patterns              │
│  - Harness State Agent: existing docs, linters, configs            │
│  - Environment Agent: dependencies, services, secrets              │
└─────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Phase 3: Delta Synthesis                                           │
│  Merge analysis results → compute what needs to be created/updated │
└─────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Phase 4: Parallel Creation/Update (spawn subagents)                │
│  - Documentation Agent: AGENTS.md, docs/*                          │
│  - Linter Agent: scripts/lint-*                                    │
│  - Config Agent: harness/*, Makefile, CI                           │
└─────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────┐
│  Phase 5: Verification + Handoff                                    │
│  Run linters, verify files, present summary                        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Quick Detection + Intent Confirmation

**Goal**: In under 5 minutes, understand project state and user intent.

### 1.1 Project State Detection

Run this quick scan:

```bash
# Count files
file_count=$(find . -type f ! -path './.git/*' ! -path './node_modules/*' ! -path './vendor/*' 2>/dev/null | wc -l)
code_files=$(find . -type f \( -name "*.go" -o -name "*.ts" -o -name "*.js" -o -name "*.py" -o -name "*.rs" \) ! -path './.git/*' ! -path './node_modules/*' ! -path './vendor/*' 2>/dev/null | wc -l)

# Check harness components
has_agents_md=$(test -f AGENTS.md && echo "yes" || echo "no")
has_architecture=$(test -f docs/ARCHITECTURE.md && echo "yes" || echo "no")
has_linters=$(ls scripts/lint-* 2>/dev/null | wc -l)
has_harness_dir=$(test -d harness && echo "yes" || echo "no")
has_makefile=$(test -f Makefile && echo "yes" || echo "no")

# Detect tech stack
if test -f go.mod; then TECH="Go"
elif test -f package.json; then TECH="TypeScript/Node.js"
elif test -f requirements.txt || test -f pyproject.toml; then TECH="Python"
else TECH="Unknown"
fi
```

### 1.2 Classify Project State

Based on detection:

| State | Criteria | Action |
|-------|----------|--------|
| **Empty** | file_count < 5 AND code_files = 0 | Guide user through project choices first |
| **Code Only** | code_files > 0 AND has_agents_md = "no" | Full analysis + full creation |
| **Partial Harness** | has_agents_md = "yes" AND (has_linters = 0 OR has_harness_dir = "no") | Gap analysis + fill gaps |
| **Full Harness** | All components exist | Audit + improvement suggestions |

### 1.3 Intent Confirmation

**If AskUserQuestion is available**, confirm scope:

```json
{
  "question": "What's your priority for this harness setup?",
  "header": "Scope",
  "multiSelect": false,
  "options": [
    {
      "label": "Full harness (Recommended)",
      "description": "Complete setup: AGENTS.md, docs, linters, eval framework, CI integration"
    },
    {
      "label": "Documentation only",
      "description": "Just AGENTS.md + docs/ for now, add linters/evals later"
    },
    {
      "label": "Minimal viable",
      "description": "Only AGENTS.md + basic lint-deps, can expand later"
    }
  ]
}
```

**If Empty project**, also ask for basics:

```json
{
  "question": "What tech stack for this project?",
  "header": "Tech Stack",
  "multiSelect": false,
  "options": [
    {"label": "Go", "description": "CLI tools, high-performance services, system programming"},
    {"label": "TypeScript/Node.js", "description": "Web APIs, full-stack apps, rapid prototyping"},
    {"label": "Python", "description": "Data processing, ML/AI, scripting"}
  ]
}
```

**If AskUserQuestion is NOT available**, use detected values and document assumptions:

```markdown
## Auto-Detected Context

| Field | Value | Confidence | Evidence |
|-------|-------|------------|----------|
| Tech Stack | {TECH} | High | Found {config file} |
| Project State | {state} | High | {criteria matched} |
| Scope | Full harness | Default | No user preference specified |

Proceeding with these assumptions. Tell me if any need adjustment.
```

---

## Phase 2: Parallel Analysis

**Goal**: Deeply understand codebase through parallel analysis agents. This speeds up the process significantly.

### 2.1 Spawn Analysis Agents

Spawn these agents **in a single message** so they run in parallel:

```
Agent("code-architecture-analysis", prompt="""
Analyze the codebase architecture:
1. Map all internal imports and build dependency graph
2. Identify layer hierarchy (which packages depend on which)
3. Extract key interfaces, types, and abstractions
4. Trace 3-5 critical code paths end-to-end
5. Catalog error handling patterns
6. Detect any circular dependencies (P0 issue!)

Tech stack: {TECH}
Output: JSON summary + detailed findings

Save results to: harness/.analysis/architecture.json
""")

Agent("harness-state-analysis", prompt="""
Audit existing harness infrastructure:
1. Check AGENTS.md: exists? accurate? right size (80-120 lines)?
2. Check docs/: ARCHITECTURE.md accuracy, design docs coverage
3. Check scripts/lint-*: layer map coverage, error message quality
4. Check harness/: eval tasks, trace format, memory structure
5. Score each dimension (0-10)
6. List gaps with priority (P0/P1/P2/P3)

Output: JSON audit report with scores and gaps

Save results to: harness/.analysis/audit.json
""")

Agent("environment-analysis", prompt="""
Detect runtime environment requirements:
1. Scan dependencies for DB drivers (postgres, mysql, sqlite, mongo)
2. Scan for service SDKs (redis, kafka, aws, gcp)
3. Find all environment variable references
4. Check for existing docker-compose.yml or k8s configs
5. Identify required secrets (never expose values!)

Output: JSON environment spec

Save results to: harness/.analysis/environment.json
""")
```

### 2.2 Wait for Analysis Completion

The agents will notify when done. While waiting, you can:
- Review any existing documentation
- Prepare templates for Phase 4

### 2.3 For Empty Projects

Skip Phase 2 analysis agents. Instead:
- Use templates from `references/greenfield-templates.md`
- Base decisions on user's tech stack choice
- Design a standard 3-layer architecture

---

## Phase 3: Delta Synthesis

**Goal**: Merge analysis results and compute exactly what needs to be created/updated.

### 3.1 Read Analysis Results

```bash
cat harness/.analysis/architecture.json
cat harness/.analysis/audit.json
cat harness/.analysis/environment.json
```

### 3.2 Compute Delta

Create a delta list:

```markdown
## Delta: What Needs to Be Done

### To Create (doesn't exist)
- [ ] AGENTS.md
- [ ] docs/ARCHITECTURE.md
- [ ] scripts/lint-deps.go
- [ ] harness/config/environment.json

### To Update (exists but has gaps)
- [ ] docs/DEVELOPMENT.md — missing build commands
- [ ] scripts/lint-quality.py — missing 3 packages in layer map

### Already Good (no changes needed)
- [x] Makefile — has all required targets
- [x] .github/workflows/ci.yml — properly configured
```

### 3.3 Confirm with User (if AskUserQuestion available)

For significant changes:

```json
{
  "question": "I've analyzed the codebase. Ready to proceed with these changes?",
  "header": "Confirm",
  "multiSelect": false,
  "options": [
    {"label": "Yes, proceed with all", "description": "Create/update all identified items"},
    {"label": "Show me the details first", "description": "I'll explain what each change involves"},
    {"label": "Only critical items", "description": "Just P0/P1 items, skip P2/P3 for now"}
  ]
}
```

---

## Phase 4: Parallel Creation/Update

**Goal**: Create or update all harness files through parallel agents.

### 4.1 Spawn Creation Agents

Based on the delta, spawn appropriate agents **in parallel**:

```
Agent("create-documentation", prompt="""
Create/update documentation files based on analysis:

Architecture data: {from architecture.json}
Existing state: {from audit.json}
Delta items: {documentation items from delta}

Files to create/update:
1. AGENTS.md — 80-120 lines, navigation map with numbered sections
2. docs/ARCHITECTURE.md — Mermaid diagrams from actual imports, layer table
3. docs/DEVELOPMENT.md — Real build/test/lint commands
4. docs/design-docs/*.md — For each key component

Requirements:
- Every claim must cite file:line from codebase
- No placeholders — real, useful content only
- Use templates from references/documentation-templates.md

Working directory: {cwd}
""")

Agent("create-linters", prompt="""
Create/update linter scripts based on analysis:

Architecture data: {from architecture.json - especially layer hierarchy}
Existing linters: {from audit.json}
Delta items: {linter items from delta}

Files to create/update:
1. scripts/lint-deps.{ext} — Layer map with ALL packages from analysis
2. scripts/lint-quality.{ext} — Code quality rules

Requirements:
- Layer map must include EVERY package (no blind spots)
- Error messages MUST be agent-actionable: WHAT + WHY + HOW
- Use templates from references/linter-templates.md

Tech stack: {TECH}
Working directory: {cwd}
""")

Agent("create-harness-config", prompt="""
Create/update harness configuration based on analysis:

Environment data: {from environment.json}
Architecture data: {from architecture.json}
Existing state: {from audit.json}
Delta items: {config items from delta}

Files to create/update:
1. harness/config/environment.json — Runtime ecosystem contract (v2.0 schema)
2. harness/scripts/setup-env.sh — Start dependencies
3. harness/scripts/start-server.sh — Start app
4. harness/scripts/teardown-env.sh — Cleanup
5. Makefile targets — lint-arch, build, test
6. .github/workflows/ci.yml — CI integration

IMPORTANT: Do NOT create harness/config/verify.json — verification configuration
is dynamically generated by harness-executor at task runtime based on
environment.json + task context (which files changed, what the task was).

Requirements:
- Follow environment collection guide from references/environment-config-guide.md
- Use ${VAR_NAME} for all secrets, never hardcode
- Scripts must be executable and self-contained
- Collect critical info (startup command, required services) via AskUserQuestion if not detectable
- Write TODO placeholders for optional missing config

Working directory: {cwd}
""")
```

### 4.2 For Empty Projects: Also Create Business Code Plan

For empty projects, add one more agent:

```
Agent("create-exec-plan", prompt="""
Create execution plan for business code (harness-executor will implement this):

Tech stack: {TECH}
Project type: {from user choice}
Architecture: 3-layer (Types → Core → Entry Points)

Create: docs/exec-plans/active/bootstrap-code.md

Contents:
- Full source code for initial project structure
- main.go/index.ts/main.py entry point
- Basic types and core logic
- Test files

This is for harness-executor to implement — not harness-creator's responsibility.
""")
```

### 4.3 Wait for Creation Completion

Agents will notify when done. Collect any issues they encountered.

---

## Phase 5: Verification + Handoff

**Goal**: Ensure everything works, then hand off or present results.

### 5.1 Run Verification

```bash
# 1. Build passes
go build ./... || npm run build || python -m compileall .

# 2. Linters pass
make lint-arch

# 3. AGENTS.md size check
wc -l AGENTS.md  # Should be 80-120 lines

# 4. All expected files exist
test -f AGENTS.md && echo "✓ AGENTS.md"
test -f docs/ARCHITECTURE.md && echo "✓ ARCHITECTURE.md"
test -f scripts/lint-deps* && echo "✓ lint-deps"
test -d harness/ && echo "✓ harness/"

# 5. Design docs exist (not just index)
find docs/design-docs -name "*.md" ! -name "index.md" | wc -l
```

### 5.2 Present Summary

```markdown
## Harness Infrastructure Complete

**Project**: {project-name}
**Tech Stack**: {TECH}
**Files Created/Updated**: {count}

### Created Files
- AGENTS.md ({N} lines)
- docs/ARCHITECTURE.md
- docs/DEVELOPMENT.md
- docs/design-docs/{component}.md
- scripts/lint-deps.{ext}
- scripts/lint-quality.{ext}
- harness/config/environment.json
- Makefile

### Verification Results
- Build: ✓
- make lint-arch: ✓
- AGENTS.md size: ✓ ({N} lines)

### Next Steps
{For empty projects: "Run harness-executor to implement business code from docs/exec-plans/active/bootstrap-code.md"}
{For existing projects: "The harness is ready. AI agents can now use AGENTS.md as their entry point."}
```

### 5.3 Automatic Handoff (for Empty Projects)

If this was an empty project with a bootstrap exec-plan, invoke harness-executor:

```
Skill(skill="harness-executor")
```

With context: "Implement the bootstrap exec-plan at docs/exec-plans/active/bootstrap-code.md"

---

## Core Principles

### 1. Repository as Single Source of Truth

Agents cannot access Slack, Google Docs, or tribal knowledge. If it's not in the repository, it doesn't exist for the agent.

### 2. AGENTS.md is a Map, Not a Manual

Keep it 80-120 lines. Link to detailed docs, don't embed them.

### 3. Enforce Invariants Mechanically

Linter errors must be agent-actionable:
```
✗ BAD: "Forbidden import in core/types/user.go"

✓ GOOD: "core/types/user.go:15 imports core/config (layer 0 → layer 2).
         Layer 0 packages must have NO internal dependencies.

         Fix options:
         1. Move config-dependent logic to a higher layer
         2. Pass the config value as a parameter
         3. Use dependency injection via an interface"
```

### 4. Build to Delete

Every component should be replaceable. Capabilities that required complex pipelines yesterday may be single prompts tomorrow.

### 5. Start Simple

Atomic, well-documented tools > complex agent choreography. Don't over-engineer.

---

## Reference Files

| File | When to Read | Contents |
|------|-------------|----------|
| `references/greenfield-templates.md` | Empty projects (Phase 2.3) | Complete Go/TS/Python scaffolding |
| `references/documentation-templates.md` | Phase 4 doc creation | Doc templates with numbered sections |
| `references/linter-templates.md` | Phase 4 linter creation | Linter code templates per language |
| `references/environment-detection-guide.md` | Phase 2 env analysis | Environment ecosystem detection |
| `references/environment-config-guide.md` | Phase 4 config creation | Startup, services, env vars, AskUserQuestion templates |

Agent prompts for Phase 2 and Phase 4 subagents are in `agents/`.

For small projects (< 20 files) or when subagents aren't available, execute phases inline instead of spawning agents.
