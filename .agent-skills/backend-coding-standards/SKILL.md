---
name: backend-coding-standards
description: "HiMarket backend coding standards for Java and Spring Boot. Use whenever modifying or reviewing himarket-server, himarket-dal, himarket-bootstrap, backend Maven POMs, Flyway SQL, controllers, services, DTOs, repositories, validation, logging, exceptions, or backend tests. Read docs/standards/backend before editing."
---

# HiMarket Backend Coding Standards

This skill is the entry point for HiMarket backend development standards. It should route the agent
to the project documentation instead of duplicating the full rules here.

## Source of Truth

The canonical backend standards live under:

- `docs/standards/backend/README.md`
- `docs/standards/backend/project-structure.md`
- `docs/standards/backend/api-controller.md`
- `docs/standards/backend/service-transaction.md`
- `docs/standards/backend/dto-json.md`
- `docs/standards/backend/dependency-management.md`
- `docs/standards/backend/data-flyway.md`
- `docs/standards/backend/security-logging.md`
- `docs/standards/backend/testing.md`

If this skill conflicts with `docs/standards/backend`, follow `docs/standards/backend`.

## Activation

Use this skill for changes in:

- `himarket-server`
- `himarket-dal`
- `himarket-bootstrap`
- backend `pom.xml` files
- Flyway migration files
- backend tests and verification scripts

## Reading Flow

Always read `docs/standards/backend/README.md` first. Then read the topic document that matches the
change:

| Change area | Read |
| --- | --- |
| Naming, annotation order, Lombok, dependency injection, imports, Stream, Optional, utilities | `docs/standards/backend/project-structure.md` |
| Controller routes, request validation, pagination, OpenAPI | `docs/standards/backend/api-controller.md` |
| Service orchestration, exceptions, transactions, events | `docs/standards/backend/service-transaction.md` |
| DTO conversion, JSON, polymorphic config | `docs/standards/backend/dto-json.md` |
| Maven dependencies and utility selection | `docs/standards/backend/dependency-management.md` |
| Entity mapping and Flyway migrations | `docs/standards/backend/data-flyway.md` |
| JavaDoc, comments, logging, sensitive data | `docs/standards/backend/security-logging.md` |
| Backend verification and test expectations | `docs/standards/backend/testing.md` |

## Working Rules

- Prefer existing code in the same layer or module as the local reference.
- For product-domain orchestration, use `ProductServiceImpl` as a useful reference, but keep smaller
  implementations simple when the scenario is smaller.
- Keep changes scoped to the user request and the touched module.
- Do not introduce unrelated refactors while applying a standard.
- Preserve user changes in the working tree.

## High-Priority Reminders

- Use constructor injection through Lombok; do not use `@Autowired`.
- Follow the documented annotation order.
- Use block JavaDoc format for field and method comments that need JavaDoc.
- Use Stream only for simple, side-effect-free collection transformations.
- Use Optional only at absent-value boundaries, not as business control flow.
- Prefer JDK, Spring, and project helpers before adding broad utility dependencies.
- Keep logs in stable English with SLF4J placeholders and the throwable as the final argument.
- Keep troubleshooting values visible unless they are credentials, tokens, secrets, or credential
  payloads.
- Add `@Validated` when controller method parameters use Jakarta validation constraints.
- Validate collection elements when blank IDs inside a list are invalid.
- Use Spring transaction annotations from `org.springframework.transaction.annotation`.

## Verification

For backend code changes, default to:

```bash
git diff --check
./mvnw -q spotless:check -DskipTests
./mvnw -q -DskipTests test-compile
```

Run targeted tests when behavior changes. For broader backend work, use one of:

- `./scripts/code-check.sh backend`
- the command set in `docs/standards/backend/testing.md`
