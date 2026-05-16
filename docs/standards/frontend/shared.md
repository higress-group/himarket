# Shared Frontend Standards

These rules apply to both HiMarket frontend applications:

- Admin console: `himarket-web/himarket-admin`
- Developer portal: `himarket-web/himarket-frontend`

## Architecture

- `pages/` owns route-level orchestration, URL params, and page-level data loading.
- `components/` owns reusable rendering and local interaction behavior.
- `hooks/` owns reusable state logic and side effects.
- `lib/` owns infrastructure code such as request wrappers and pure utilities.
- `types/` owns cross-module TypeScript types.

## Components

- Page components use default exports when that is the existing app convention.
- Reusable components use named exports.
- Keep component props explicit and typed.
- Extract state-heavy logic into hooks when the component becomes hard to scan.
- Extract pure display blocks when markup dominates the component.

## TypeScript

- Use `import type` for type-only imports.
- Avoid `any`; prefer typed API contracts, discriminated unions, and type guards.
- Use optional fields with `?:` instead of `| undefined`.
- Keep type definitions close to their reuse scope:
  - shared app-wide types in `types/`
  - API contracts in `lib/apis/` or the app's established API directory
  - component-local types in the component file

## Data Fetching

- Keep API request details out of visual components when the request is reusable.
- Keep loading, empty, and error states explicit.
- Do not duplicate request response shape transformations across multiple pages.

## UI

- Follow the existing app's Ant Design and Tailwind patterns.
- Keep admin UI dense, scannable, and task-oriented.
- Keep developer portal UI clear, product-facing, and consistent with its existing navigation.
- Do not add new visual systems or component libraries without a clear reason.

## Verification

- Run the relevant app checks after frontend changes:
  - admin console: commands under `himarket-web/himarket-admin`
  - developer portal: commands under `himarket-web/himarket-frontend`
- For UI changes, manually verify the affected desktop and responsive states when practical.
