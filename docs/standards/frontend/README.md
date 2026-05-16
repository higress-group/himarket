# HiMarket Frontend Coding Standards

Use this document as the frontend standards entry point. Shared frontend rules are separated from
application-specific rules so the admin console and developer portal can evolve independently while
keeping the same baseline.

## Scope

These standards apply to new frontend code and frontend code being actively modified. Existing code
that does not fully comply should be treated as cleanup debt, not as a pattern to copy.

## Topic Documents

| Area | Document |
| ---- | -------- |
| Shared frontend rules | [shared.md](shared.md) |
| Admin console frontend | [admin.md](admin.md) |
| Developer portal frontend | [portal.md](portal.md) |

## Core Rules

- Use React function components and Hooks.
- Keep route-level orchestration in `pages/`.
- Put reusable UI in `components/`.
- Put cross-domain reusable state logic in `hooks/`.
- Put request wrappers and API types in `lib/` or the app's established API directory.
- Use `import type` for pure TypeScript type imports.
- Avoid `any`; prefer typed request/response contracts, type guards, or explicit union types.
- Keep user-facing text and UI behavior consistent with the target app.
- Run the app-specific formatter, ESLint, and TypeScript checks before submitting changes.
