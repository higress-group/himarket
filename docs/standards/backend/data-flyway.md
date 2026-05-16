# Backend Data and Flyway Standards

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## Database and Flyway

Database schema changes are managed by Flyway under
`himarket-bootstrap/src/main/resources/db/migration`.

**Migration naming:**

- Use `V{number}__Description.sql`.
- Keep version numbers monotonic and unique.
- Use short English descriptions with underscores: `V18__Add_api_definition_tables.sql`.

**Rules:**

- Never modify a migration script that has been released or applied in a shared environment.
  Add a new migration instead.
- Migrations that exist only in a local, unreleased test environment may be merged or renumbered
  before sharing.
- Migration scripts should be idempotent where practical and safe to re-run in local or test
  environments. Flyway versioned migrations are applied once, but the SQL should still tolerate
  local rebuilds, partial manual checks, and drifted test environments without corrupting data.
- Prefer idempotent DDL where the database supports it, such as `CREATE TABLE IF NOT EXISTS`,
  `DROP TABLE IF EXISTS`, `ADD COLUMN IF NOT EXISTS`, and guarded index or constraint creation.
- Data migrations must be deterministic and idempotent. Use `WHERE NOT EXISTS`,
  `ON DUPLICATE KEY UPDATE`, or equivalent guards instead of blindly inserting duplicate rows.
- Keep DDL explicit: column type, length, nullability, defaults, indexes, and unique constraints
  should be intentional.
- Add indexes for new query predicates and unique constraints for business uniqueness.
- Avoid destructive DDL. If a destructive change is required, document why it is safe.
- Keep data migrations small. Do not store secrets or environment-specific values in migration
  scripts.
- Entity mappings and migration scripts must evolve together.

```sql
CREATE TABLE api_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_definition_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_api_definition_id (api_definition_id),
    KEY idx_api_definition_product_id (product_id)
);
```

---
