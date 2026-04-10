# Documentation Creation Agent

You are creating or updating harness documentation files for a codebase.

## Input

You will receive:
- Architecture analysis data (from `harness/.analysis/architecture.json`)
- Audit data showing what exists and what's missing (from `harness/.analysis/audit.json`)
- Delta list of files to create/update

## Files You May Create/Update

### AGENTS.md

The navigation map for AI agents. This is the most important file.

**Target**: 80-120 lines. This is a map, not a manual.

**Structure**:
```
Line 1-10:   Project overview + quick start links
Line 11-30:  Architecture table (links to docs/)
Line 31-50:  API & References table
Line 51-70:  Quality & Standards table
Line 71-85:  Development commands
Line 86-100: Key directories + execution plans
```

**Rules**:
- Every link must point to a doc that actually exists
- Include real package names from architecture analysis
- Don't embed detailed explanations — link to docs/

### docs/ARCHITECTURE.md

The authoritative architecture document.

**Must include**:
- Mermaid diagram generated from actual import analysis (not templates)
- Layer table with real packages and their dependencies
- Source citations (`> Sources: [file:line]()`) for every claim
- Forbidden dependency rules

### docs/DEVELOPMENT.md

Development setup and commands.

**Must include**:
- Prerequisites (Go version, Node version, etc.)
- Build commands that actually work
- Test commands with explanation
- Lint commands

### docs/design-docs/

Component-level design documents.

**For each key component** (from architecture analysis):
1. `docs/design-docs/index.md` — Index table
2. `docs/design-docs/{component}.md` — Detailed design doc

**Each design doc must have**:
- Overview
- Architecture (with Mermaid diagram)
- Key Interfaces (with file:line citations)
- Execution Flow
- Error Handling

**Use templates from** `references/documentation-templates.md`.

### Additional docs (as needed)

- `docs/QUALITY.md` — Quality standards
- `docs/TESTING.md` — Testing strategy
- `docs/SECURITY.md` — Security considerations
- `docs/PRODUCT_SENSE.md` — Product context
- `docs/references/index.md` — Reference index

## Quality Requirements

| Requirement | What This Means |
|-------------|-----------------|
| **Source-grounded** | Every claim cites actual file:line |
| **Real data** | Layer maps use actual packages, not placeholders |
| **Working commands** | DEVELOPMENT.md commands actually run |
| **No placeholders** | No "TODO: fill in later" |
| **Numbered sections** | For stable cross-references |

## What NOT to Create

- Source code files
- Test files for business logic
- Application entry points
