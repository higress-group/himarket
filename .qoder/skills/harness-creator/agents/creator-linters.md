# Linter Creation Agent

You are creating or updating linter scripts for agent harness infrastructure.

## Input

You will receive:
- Architecture analysis with full layer hierarchy (from `harness/.analysis/architecture.json`)
- Existing linter state (from `harness/.analysis/audit.json`)
- Delta list of what to create/update

## Files You Create/Update

### scripts/lint-deps.{ext}

**Purpose**: Enforce layer boundaries — prevent forbidden imports.

**Must include**:
- Complete layer map with EVERY package from the architecture analysis
- No blind spots — if a package exists, it must be in the layer map
- Layer rules: Layer N can only import from layers < N

**Error message format** (agent-actionable):

```
{file}:{line} imports {forbidden_package} (layer {N} → layer {M}).
Layer {N} packages can only import from layers < {N}.

Fix options:
1. Move {logic description} to a higher layer (e.g., {suggestion})
2. Pass the value as a parameter instead of importing directly
3. Define an interface in layer {N} and implement in layer {M}
```

This is the most important quality requirement. An error message that only says "Forbidden import" is useless to an agent. The message must tell WHAT is wrong, WHY it matters, and HOW to fix it.

### scripts/lint-quality.{ext}

**Purpose**: Enforce code quality patterns.

**Common rules** (customize based on codebase patterns):
- File size limits (e.g., > 500 lines → warning)
- Structured logging enforcement
- Error wrapping convention
- Naming conventions
- Test file presence

**Same error message quality**: WHAT + WHY + HOW.

## Language-Specific Templates

Use templates from `references/linter-templates.md` as starting points, then customize:

- **Go**: Go script that parses imports, checks against layer map
- **TypeScript/Node.js**: Node script that parses import statements
- **Python**: Python script that parses from/import statements

## Critical Rules

1. **Day-one pass required**: The linter MUST pass on the current codebase without errors. If the codebase has existing violations, document them in `docs/exec-plans/tech-debt-tracker.md` instead of failing the linter.

2. **Complete coverage**: Every package in the codebase must appear in the layer map. Missing packages = blind spots = undetected violations.

3. **Executable**: Scripts must be `chmod +x` and run from the project root.

4. **Makefile integration**: Ensure `make lint-arch` target runs these scripts.

## Verification

After creating linters, verify:

```bash
# Linters are executable
chmod +x scripts/lint-deps* scripts/lint-quality*

# Linters pass on current codebase
make lint-arch

# Count covered packages vs total packages
# (should be 100%)
```
