---
name: frontend-coding-standards
description: "HiMarket frontend coding standards for React, TypeScript, Vite, Ant Design, Tailwind, i18n, API calls, component structure, and frontend verification. Use whenever modifying or reviewing himarket-web/himarket-frontend or himarket-web/himarket-admin. Read docs/standards/frontend before editing."
---

# HiMarket Frontend Coding Standards

This skill is the entry point for HiMarket frontend development standards. It should route the agent
to the project documentation instead of duplicating the full rules here.

## Source of Truth

The canonical frontend standards live under:

- `docs/standards/frontend/README.md`
- `docs/standards/frontend/shared.md`
- `docs/standards/frontend/admin.md`
- `docs/standards/frontend/portal.md`

If this skill conflicts with `docs/standards/frontend`, follow `docs/standards/frontend`.

## Activation

Use this skill for changes in:

- `himarket-web/himarket-admin`
- `himarket-web/himarket-frontend`
- shared frontend build, lint, type, i18n, style, or API client code

## Reading Flow

Always read `docs/standards/frontend/README.md` first.

Then read:

- `docs/standards/frontend/shared.md` for every frontend change.
- `docs/standards/frontend/admin.md` when touching `himarket-web/himarket-admin`.
- `docs/standards/frontend/portal.md` when touching `himarket-web/himarket-frontend`.

If a change affects both frontend applications, read both application-specific documents.

## Working Rules

- Follow the existing application structure before introducing a new pattern.
- Keep shared abstractions small and justified by repeated use.
- Keep component, hook, API, i18n, and styling changes scoped to the touched feature.
- Preserve user changes in the working tree.

## High-Priority Reminders

- Use strict TypeScript; do not use `any` or non-null assertions to bypass typing.
- Keep imports, object keys, and JSX props ordered according to the local ESLint rules.
- Keep API calls in the documented API layer and follow the request/response shape conventions.
- Guard initialization requests that can be double-invoked by React StrictMode.
- Use i18n resources for user-facing copy.
- Keep comments useful and remove dead commented-out code.
- Prefer existing UI tokens, Ant Design conventions, Tailwind utilities, and local components before
  introducing new styling patterns.
- Do not add broad frontend dependencies for problems already solved by the project stack.

## Verification

Run checks in each changed frontend application directory:

```bash
npm run lint
npm run type-check
npm run format:check
```

For broad changes from the project root, use one of:

- `./scripts/code-check.sh frontend`
- `./scripts/code-check.sh admin`
- `./scripts/code-check.sh all`
