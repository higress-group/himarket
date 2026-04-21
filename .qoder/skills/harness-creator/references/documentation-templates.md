# Documentation Templates

Templates for creating the documentation architecture. Following DeepWiki principles:
every claim is grounded in source code, diagrams are auto-generated from analysis, and
navigation uses a consistent numbered hierarchy.

## Table of Contents

- [Numbered Section Convention](#numbered-section-convention)
- [Source Citation Convention](#source-citation-convention)
- [AGENTS.md Template](#agentsmd-template)
- [ARCHITECTURE.md Template](#architecturemd-template)
- [DEVELOPMENT.md Template](#developmentmd-template)
- [QUALITY.md Template](#qualitymd-template)
- [Design Document Template](#design-document-template)
- [API Reference Template](#api-reference-template)
- [Reference Document Template](#reference-document-template)

---

## Numbered Section Convention

All documentation uses a hierarchical numbered section system. This enables stable cross-references
between documents and makes navigation predictable for both humans and agents.

**Rules:**
1. Top-level sections use single numbers: `## 1`, `## 2`, `## 3`
2. Sub-sections use decimals: `### 1.1`, `### 1.2`, `### 2.1`
3. Cross-references use the section number: "See [2.3 Data Flow](#23-data-flow)"
4. Every project's docs share the same numbering scheme per document type

This matters because agents navigating documentation need stable anchors. If you renumber,
links break. If you don't number at all, cross-references become vague. Numbered sections
also let agents quickly locate specific information — "jump to section 3.2" is unambiguous.

---

## Source Citation Convention

Every technical statement in the documentation should cite its source code location. This is
what makes the documentation trustworthy and verifiable. When docs say "the auth module uses
JWT tokens", it should point to the exact file and line range where that happens.

**Format:**
```
> Sources: [`internal/auth/jwt.go:25-48`](), [`internal/types/token.go:10-15`]()
```

**Rules:**
1. Place source citations after each major claim, table, or diagram
2. Use the format `file:line` or `file:line-range`
3. Group related sources on a single `> Sources:` line
4. For generated diagrams, cite the files that were analyzed to produce the diagram
5. When source files change, regenerate the citation (this is what keeps docs honest)

**Why this matters**: When an agent reads "Layer 0 packages cannot import core/", it needs
to know *where* that rule is enforced (in `scripts/lint-deps.go:42`) so it can verify the
claim and understand what happens when the rule is broken.

---

## AGENTS.md Template

```markdown
# [Project Name] Agent Guide

This file provides navigation to AI agents working with this repository.

## 1 Quick Start

- [Architecture Overview](docs/ARCHITECTURE.md) — System design, layers, data flow
- [Development Setup](docs/DEVELOPMENT.md) — Build, test, environment

## 2 Architecture

| Section | Document | Description |
|---------|----------|-------------|
| 2.1 | [System Architecture](docs/ARCHITECTURE.md) | Layer hierarchy, dependency graph, data flow |
| 2.2 | [Component A](docs/design/component-a.md) | Purpose, interfaces, key types |
| 2.3 | [Component B](docs/design/component-b.md) | Purpose, interfaces, key types |
| 2.4 | [Component C](docs/design/component-c.md) | Purpose, interfaces, key types |

## 3 API & References

| Section | Document | Description |
|---------|----------|-------------|
| 3.1 | [API Catalog](docs/references/api.md) | Complete API reference |
| 3.2 | [Error Codes](docs/references/error-codes.md) | Error code taxonomy |
| 3.3 | [Configuration](docs/references/config.md) | Configuration schema |

## 4 Quality & Standards

| Section | Document | Description |
|---------|----------|-------------|
| 4.1 | [Code Quality](docs/QUALITY.md) | Golden principles, linter rules |
| 4.2 | [Security Policy](docs/SECURITY.md) | Security considerations |
| 4.3 | [Testing Standards](docs/TESTING.md) | Test patterns, coverage |
| 4.4 | [Quality Score](docs/QUALITY_SCORE.md) | Per-domain quality grades |

## 5 Development

\`\`\`bash
make build      # Build project
make test       # Run tests
make lint-arch  # Run architecture linters
\`\`\`

See [Development Setup](docs/DEVELOPMENT.md) for complete reference.

## 6 Execution Plans

- [Active Plans](docs/exec-plans/active/) — Work in progress
- [Completed Plans](docs/exec-plans/completed/) — Historical record
- [Tech Debt Tracker](docs/exec-plans/tech-debt-tracker.md)

## 7 Key Directories

| Directory | Layer | Purpose |
|-----------|-------|---------|
| \`cmd/\` | L5 | CLI entry points |
| \`internal/core/\` | L3 | Business logic |
| \`internal/types/\` | L0 | Type definitions |
| \`docs/\` | — | All documentation |
| \`scripts/\` | — | Linters and tooling |
| \`harness/\` | — | Eval and quality |

---

**Note**: This file is a navigation map (~100 lines). Details live in linked documents.
```

---

## ARCHITECTURE.md Template

This is the most important technical document. It should read like a DeepWiki architecture page:
grounded in source, with Mermaid diagrams, and specific file+line references.

```markdown
# Architecture

> Last regenerated: YYYY-MM-DD
> Source analysis: [\`go.mod\`](), [\`cmd/\`](), [\`internal/\`]()

## 1 Overview

[One paragraph: what this project does, who uses it, and the core architectural pattern.
For example: "This is a CLI tool that processes X. It follows a layered architecture with
strict dependency direction enforcement."]

## 2 System Architecture

### 2.1 Package Dependency Graph

\`\`\`mermaid
graph TD
    subgraph "Layer 3 — Entry Points"
        CMD[cmd/]
    end
    subgraph "Layer 2 — Business Logic"
        CORE[internal/core/]
    end
    subgraph "Layer 1 — Utilities"
        UTIL[internal/utils/]
    end
    subgraph "Layer 0 — Types"
        TYPES[internal/types/]
    end

    CMD --> CORE
    CORE --> UTIL
    UTIL --> TYPES
    CORE --> TYPES

    style TYPES fill:#e8f5e9
    style UTIL fill:#e3f2fd
    style CORE fill:#fff3e0
    style CMD fill:#f3e5f5
\`\`\`

> Sources: [\`go.mod\`](), [\`cmd/root.go:1-15\`](), [\`internal/core/core.go:1-10\`]()

### 2.2 Layer Hierarchy

| Layer | Packages | Can Import | Cannot Import |
|-------|----------|------------|---------------|
| L0 | \`internal/types/\` | stdlib only | anything internal |
| L1 | \`internal/utils/\` | L0 | L2, L3 |
| L2 | \`internal/core/\` | L0, L1 | L3 |
| L3 | \`cmd/\` | L0, L1, L2 | — |

> Enforced by: [\`scripts/lint-deps.go\`]()

### 2.3 Forbidden Dependencies

These are not style suggestions — they are mechanically enforced by the linter:

- \`internal/types/\` must not import any internal package
- \`internal/core/\` must not import \`cmd/\`
- Peer packages at the same layer must not import each other

> Enforced by: [\`scripts/lint-deps.go:30-45\`]()

## 3 Core Components

### 3.1 [Component A Name]

**Purpose**: [what it does in one sentence]
**Location**: \`internal/core/component_a.go\`
**Lines**: ~XXX

\`\`\`mermaid
classDiagram
    class ComponentA {
        <<interface>>
        +Method1(ctx context.Context) error
        +Method2(input Input) (Output, error)
    }
    note for ComponentA "Defined in internal/core/component_a.go:18"
\`\`\`

**Key Types:**

| Type | File | Line | Purpose |
|------|------|------|---------|
| \`ComponentA\` | \`internal/core/component_a.go\` | 18 | Main interface |
| \`componentAImpl\` | \`internal/core/component_a.go\` | 35 | Default implementation |
| \`ComponentAConfig\` | \`internal/types/config.go\` | 22 | Configuration options |

> Sources: [\`internal/core/component_a.go:18-65\`](), [\`internal/types/config.go:22-30\`]()

### 3.2 [Component B Name]

[Same structure as 3.1]

## 4 Data Flow

### 4.1 Primary Request Flow

\`\`\`mermaid
sequenceDiagram
    participant User
    participant CLI as CLI (cmd/root.go)
    participant Core as Core (internal/core/)
    participant Store as Storage (internal/storage/)

    User->>CLI: command args
    CLI->>Core: Process(args)
    Core->>Store: Read/Write data
    Store-->>Core: Result
    Core-->>CLI: Output
    CLI-->>User: Display
\`\`\`

> Sources: [\`cmd/root.go:42-58\`](), [\`internal/core/processor.go:15-33\`]()

### 4.2 Error Handling Flow

\`\`\`
User Input → Validate → Process → Output
                ↓           ↓
            ValidationErr  ProcessErr
                ↓           ↓
            Error Response (with typed error code)
\`\`\`

> See [3.2 Error Codes](references/error-codes.md) for the complete error taxonomy.

## 5 Critical Files

| File | Lines | Purpose | Key Exports |
|------|-------|---------|-------------|
| \`main.go\` | ~15 | Entry point | \`main()\` |
| \`cmd/root.go\` | ~80 | CLI structure | \`Execute()\` |
| \`internal/types/types.go\` | ~100 | Type definitions | \`Config\`, \`Result\`, \`Error\` |
| \`internal/core/core.go\` | ~200 | Business logic | \`Process()\`, \`Validate()\` |

## 6 Key Design Decisions

| # | Decision | Rationale | Alternatives Considered |
|---|----------|-----------|------------------------|
| 1 | [Decision A] | [Why this was chosen] | [What else was considered] |
| 2 | [Decision B] | [Why this was chosen] | [What else was considered] |
| 3 | [Decision C] | [Why this was chosen] | [What else was considered] |

## 7 Module & Dependencies

\`\`\`
module [module-path]
go X.XX
\`\`\`

**External Dependencies:**

| Dependency | Version | Purpose |
|------------|---------|---------|
| \`dep-a\` | v1.2.3 | [Purpose] |
| \`dep-b\` | v4.5.6 | [Purpose] |

> Sources: [\`go.mod\`](), [\`go.sum\`]()

## See Also

- [2.2 Component A Design](design/component-a.md)
- [2.3 Component B Design](design/component-b.md)
- [3.1 API Catalog](references/api.md)
```

---

## DEVELOPMENT.md Template

```markdown
# Development Setup

## 1 Prerequisites

- Language runtime X.XX+
- Build tool Y
- Dependencies Z

## 2 Quick Start

\`\`\`bash
# Clone and setup
git clone <repo>
cd <project>

# Install dependencies
make deps

# Build
make build

# Test
make test
\`\`\`

## 3 Build Commands

| Command | Description | Duration |
|---------|-------------|----------|
| \`make build\` | Build for current platform | ~5s |
| \`make test\` | Run all tests | ~30s |
| \`make lint\` | Run all linters | ~10s |
| \`make lint-arch\` | Run architecture linters | ~5s |
| \`make clean\` | Clean build artifacts | ~1s |

## 4 Test Commands

| Command | Description | Scope |
|---------|-------------|-------|
| \`make test\` | All tests | Full |
| \`go test -v ./path/...\` | Specific package | Package |
| \`go test -run TestX ./\` | Specific test | Single |

## 5 Project Structure

\`\`\`
.
├── cmd/           — CLI commands (Layer 3)
├── internal/
│   ├── core/      — Business logic (Layer 2)
│   ├── types/     — Type definitions (Layer 0)
│   └── utils/     — Utilities (Layer 1)
├── docs/          — Documentation
├── scripts/       — Linters and tooling
├── harness/       — Eval & quality
└── tests/         — Test fixtures
\`\`\`

## 6 Configuration

| Config File | Location | Purpose |
|-------------|----------|---------|
| User config | \`~/.project/config.json\` | Per-user settings |
| Project config | \`.project/config.json\` | Project-wide settings |

## 7 Environment Variables

| Variable | Default | Required | Description |
|----------|---------|----------|-------------|
| \`API_KEY\` | — | Yes | API authentication |
| \`DEBUG\` | \`false\` | No | Enable debug mode |
| \`LOG_LEVEL\` | \`info\` | No | Logging verbosity |
```

---

## QUALITY.md Template

```markdown
# Quality Standards

Golden principles enforced by \`scripts/lint-quality.go\`.

## 1 Structured Logging

> Enforced by: [\`scripts/lint-quality.go:28-45\`]()

\`\`\`go
// ✓ Good — structured, parseable, queryable
logger.Info("operation completed", zap.String("key", value))

// ✗ Bad — unstructured, hard to parse
log.Printf("operation completed: %v", value)
\`\`\`

## 2 Error Handling

### 2.1 Use Typed Errors
\`\`\`go
// ✓ Good — typed, machine-readable
return NewError(ErrCodeNotFound, "resource not found")

// ✗ Bad — stringly typed
return fmt.Errorf("not found")
\`\`\`

### 2.2 Wrap with Context
\`\`\`go
// ✓ Good — preserves error chain
return fmt.Errorf("reading config: %w", err)

// ✗ Bad — loses context
return err
\`\`\`

## 3 File Size Limits

> Enforced by: [\`scripts/lint-quality.go:50-62\`]()

- Maximum **1000 lines** per file
- Split large files into focused modules
- When a file approaches 800 lines, plan the split

## 4 Naming Conventions

| Category | Convention | Example |
|----------|-----------|---------|
| Types | PascalCase | \`UserConfig\` |
| Exported Functions | PascalCase | \`NewConfig()\` |
| Unexported Functions | camelCase | \`parseInput()\` |
| Constants | PascalCase | \`MaxRetries\` |
| Packages | lowercase | \`config\`, \`auth\` |

## 5 Enforcement

\`\`\`bash
make lint-arch    # Run all architecture linters
\`\`\`

See [\`scripts/lint-quality.go\`]() for the complete set of enforced rules.
```

---

## Design Document Template

Design documents go deeper than the architecture overview. Each one covers a single component
with enough detail for an agent to understand the component's role, modify it correctly,
and verify the modification is sound.

```markdown
# [Component Name]

> Last updated: YYYY-MM-DD
> Primary source: [\`path/to/main/file.go\`]()

## 1 Overview

[What this component does and why it exists. One paragraph max.]

## 2 Architecture

### 2.1 Component Diagram

\`\`\`mermaid
graph TD
    A[Public API] --> B[Internal Logic]
    B --> C[Storage]
    B --> D[External Service]
\`\`\`

> Sources: [\`internal/core/component.go\`]()

### 2.2 Key Interfaces

\`\`\`go
// Defined in internal/core/component.go:18
type Component interface {
    Method1() Result
    Method2(input Input) (Output, error)
}
\`\`\`

> Sources: [\`internal/core/component.go:18-24\`]()

### 2.3 Key Types

| Type | File | Line | Fields | Purpose |
|------|------|------|--------|---------|
| \`componentImpl\` | \`component.go\` | 35 | \`field1\`, \`field2\` | Default implementation |
| \`ComponentConfig\` | \`types.go\` | 22 | \`timeout\`, \`retries\` | Configuration |

> Sources: [\`internal/core/component.go:35-50\`](), [\`internal/types/types.go:22-28\`]()

## 3 Execution Flow

\`\`\`mermaid
sequenceDiagram
    participant Caller
    participant Comp as Component
    participant Dep as Dependency

    Caller->>Comp: Method1()
    Comp->>Dep: FetchData()
    Dep-->>Comp: Data
    Comp-->>Caller: Result
\`\`\`

> Sources: [\`internal/core/component.go:55-72\`]()

## 4 Configuration

| Option | Type | Default | Validation | Description |
|--------|------|---------|------------|-------------|
| \`timeout\` | \`time.Duration\` | \`30s\` | > 0 | Request timeout |
| \`retries\` | \`int\` | \`3\` | 0-10 | Max retry count |

> Sources: [\`internal/types/config.go:15-25\`]()

## 5 Error Handling

| Error | Code | When | Recovery |
|-------|------|------|----------|
| \`ErrNotFound\` | 1001 | Resource doesn't exist | Return 404 |
| \`ErrTimeout\` | 1002 | Request exceeded timeout | Retry with backoff |

## 6 Usage Examples

\`\`\`go
// Basic usage
comp := NewComponent(config)
result, err := comp.Method1()
if err != nil {
    // Handle typed error
}
\`\`\`

## 7 Testing

| Test File | Coverage | What It Tests |
|-----------|----------|---------------|
| \`component_test.go\` | 85% | Core logic, edge cases |
| \`component_integration_test.go\` | 70% | End-to-end with dependencies |

## See Also

- [Architecture Overview](../ARCHITECTURE.md#31-component-name) — Section 3.1
- [Related Component](related.md) — How it interacts with this component
```

---

## API Reference Template

For projects with a public API surface (HTTP, gRPC, library, CLI).

```markdown
# API Reference

> Auto-generated from source analysis on YYYY-MM-DD

## 1 Summary

| Endpoint / Function | Method | Purpose |
|---------------------|--------|---------|
| \`/api/v1/users\` | GET | List users |
| \`/api/v1/users/:id\` | GET | Get user by ID |
| \`/api/v1/users\` | POST | Create user |
| \`NewClient(cfg)\` | — | Create API client |

## 2 Endpoints

### 2.1 List Users

\`\`\`
GET /api/v1/users?page=1&limit=20
\`\`\`

**Parameters:**

| Name | Type | Required | Default | Description |
|------|------|----------|---------|-------------|
| \`page\` | int | No | 1 | Page number |
| \`limit\` | int | No | 20 | Items per page |

**Response:**

\`\`\`json
{
  "users": [{"id": "abc", "name": "Alice"}],
  "total": 42,
  "page": 1
}
\`\`\`

> Handler: [\`api/handlers/user.go:25-48\`]()
> Types: [\`internal/types/user.go:10-18\`]()

### 2.2 Get User

[Same structure]

## 3 Error Responses

| Status | Code | Message | When |
|--------|------|---------|------|
| 400 | \`INVALID_INPUT\` | "Invalid request body" | Malformed JSON |
| 404 | \`NOT_FOUND\` | "Resource not found" | ID doesn't exist |
| 500 | \`INTERNAL\` | "Internal server error" | Unexpected failure |

> Error types defined in: [\`internal/types/errors.go:12-35\`]()
```

---

## Reference Document Template

```markdown
# [Reference Name]

> Last updated: YYYY-MM-DD
> Extracted from: [\`path/to/source\`]()

## 1 Overview

Complete reference for [topic].

## 2 Summary Table

| Item | Constant | Category | File | Line | Description |
|------|----------|----------|------|------|-------------|
| A | \`ConstA\` | Cat1 | \`types.go\` | 15 | Does X |
| B | \`ConstB\` | Cat2 | \`types.go\` | 22 | Does Y |

## 3 Details

### 3.1 Item A

[Detailed description]

\`\`\`go
// From types.go:15
const ConstA = "value"
\`\`\`

> Sources: [\`internal/types/types.go:15\`]()

### 3.2 Item B

[Detailed description]

> Sources: [\`internal/types/types.go:22\`]()

## See Also

- [Related Reference](related.md)
```
