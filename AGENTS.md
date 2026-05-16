# AGENTS.md

<p align="center">
  <b>English</b> | <a href="AGENTS_zh.md">简体中文</a>
</p>

**Always respond in Simplified Chinese.**

## 1. Project Overview

HiMarket is an AI open platform that provides API product management, a developer portal, AI chat,
cloud IDE capabilities through HiCoding, and MCP Server hosting.

This repository is a frontend/backend monorepo composed of three backend modules, two frontend
applications, project documentation, and helper scripts. Before making changes, identify which
module the task touches, then read the relevant directory and standards document.
Backend changes usually live in `himarket-server/`, while entity, repository, or database migration
changes may also touch `himarket-dal/` and `himarket-bootstrap/`. Frontend changes should first
distinguish between the admin console and the developer portal.

| Module | Path | Responsibility |
| ------ | ---- | -------------- |
| Backend data layer | `himarket-dal/` | Entities, repositories, database converters, enums, and data-layer support code |
| Backend business layer | `himarket-server/` | Controllers, services, DTOs, authorization, product import, gateway, Nacos, MCP, and other core backend logic |
| Backend bootstrap layer | `himarket-bootstrap/` | Spring Boot entry point, runtime configuration, packaging configuration, and Flyway migrations |
| Admin console | `himarket-web/himarket-admin/` | Admin-facing product, gateway, user, portal, and management workflows |
| Developer portal | `himarket-web/himarket-frontend/` | Developer-facing product browsing, subscription, AI chat, and HiCoding workflows |
| Project documentation | `docs/` | Architecture, design notes, coding standards, and external source indexes |
| Helper scripts | `scripts/` | Local startup, quality checks, and external repository setup |

## 2. Key Documents

Code changes must follow the relevant standards under `docs/standards/`. Code style,
implementation conventions, API design, database migration rules, frontend organization, and review
criteria are defined there.
If a task touches an unfamiliar module, read the system architecture or the corresponding standards
document before making changes. The table below lists the most common entry points.

| Document | Path |
| -------- | ---- |
| Coding standards index | `docs/standards/README.md` |
| Backend standards | `docs/standards/backend/README.md` |
| Frontend standards | `docs/standards/frontend/README.md` |
| System architecture | `docs/ARCHITECTURE.md` |
| Contributing guide | `CONTRIBUTING.md` / `CONTRIBUTING_zh.md` |
| User guide | `USER_GUIDE.md` / `USER_GUIDE_zh.md` |

## 3. Execution Principles

These principles constrain how an Agent should work in this repository. Keep changes focused,
reuse existing local patterns, and complete necessary verification when behavior changes.

- Read the relevant standards and nearby implementations before editing.
- Change only task-related files; avoid unrelated refactors.
- Do not revert user or other Agent changes unless explicitly requested.
- If documentation and code disagree, follow the code and the latest standards, and call out the
  difference when needed.
- For changes involving APIs, databases, permissions, imports, gateways, Nacos, MCP, or config
  parsing, prefer a verifiable end-to-end check.
- After completing code changes, review the modified code against the relevant frontend or backend
  standards under `docs/standards/`, and call out any remaining risks, deviations, or unverified
  assumptions.

## 4. Command Guide

These commands cover the common compile, startup, and quality-check workflows. Choose the smallest
check that matches the change scope; use the full check before submission or after cross-module
changes.

| Command | Purpose | Use When |
| ------- | ------- | -------- |
| `make compile` | Quickly compiles the backend, skipping tests and format checks | Confirming Java changes compile |
| `./scripts/run.sh` | Builds and starts the backend service | Local API verification is needed |
| `./scripts/code-check.sh` | Runs full backend and frontend quality checks | Before submission or after cross-module changes |
| `./scripts/code-check.sh backend` | Checks backend Java code only | Backend-only changes |
| `./scripts/code-check.sh frontend` | Checks the developer portal frontend only | Changes under `himarket-web/himarket-frontend/` |
| `./scripts/code-check.sh admin` | Checks the admin console frontend only | Changes under `himarket-web/himarket-admin/` |
| `git diff --check` | Checks whitespace and formatting issues in the diff | Documentation-only or lightweight changes |

For additional commands, use `Makefile` and `scripts/` as the source of truth.

## 5. Operating Limits

These limits take priority over normal execution habits. They prevent workspace damage, sensitive
data leaks, and commands that can hang. Ask the user before any uncertain destructive operation.

- Do not use HEREDOC syntax (`<<EOF`, `<<'EOF'`, etc.).
- Do not use commands that may wait for interactive input.
- Do not run destructive commands unless the user explicitly requests them.
- Do not modify database data unless the user explicitly requests it.
- Do not expose full secrets, tokens, passwords, authorization headers, or connection strings.
