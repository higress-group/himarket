# HiMarket Coding Standards

This directory is the source of truth for HiMarket coding standards. Keep standards here so they
are easy to discover, review, and maintain over time.

## Scope

These standards apply to new code and code being actively modified. Existing code that does not
fully comply should be treated as cleanup debt, not as a pattern to copy.

## Standards Index

| Area | Document |
| ---- | -------- |
| Backend overview | [backend/README.md](backend/README.md) |
| Backend project structure and style | [backend/project-structure.md](backend/project-structure.md) |
| Backend API, Controller, OpenAPI, REST | [backend/api-controller.md](backend/api-controller.md) |
| Backend Service, errors, transactions, events | [backend/service-transaction.md](backend/service-transaction.md) |
| Backend data and Flyway | [backend/data-flyway.md](backend/data-flyway.md) |
| Backend DTO and JSON | [backend/dto-json.md](backend/dto-json.md) |
| Backend dependency management | [backend/dependency-management.md](backend/dependency-management.md) |
| Backend security and logging | [backend/security-logging.md](backend/security-logging.md) |
| Backend testing | [backend/testing.md](backend/testing.md) |
| Frontend overview | [frontend/README.md](frontend/README.md) |
| Frontend shared rules | [frontend/shared.md](frontend/shared.md) |
| Admin frontend | [frontend/admin.md](frontend/admin.md) |
| Developer portal frontend | [frontend/portal.md](frontend/portal.md) |

## Maintenance Rules

- Add new coding standards under `docs/standards/`.
- Keep module-local legacy standard files as short redirects only.
- Prefer small focused documents over one large document.
- Put rules that apply to all backend code in `backend/README.md`; put detailed examples in the
  matching topic document.
- Put frontend rules shared by both applications in `frontend/shared.md`; put application-specific
  rules in `frontend/admin.md` or `frontend/portal.md`.
- Use English for code-facing standards, examples, comments, OpenAPI text, and log messages.
