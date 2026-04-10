# Database Verification Mixin

This mixin adds database verification capabilities to the verifier.

## When to Include

Include this mixin when:
- `environment.json` has `databases` configured
- The task involves data persistence
- Side effects need database-level verification

## Additional Context

You are verifying that database operations work correctly. This includes
checking that data is persisted, constraints are enforced, and queries work.

## Database-Specific Protocol

### Database Connection

1. Read database config from `environment.json`:
   ```json
   {
     "databases": [
       {"type": "postgres", "name": "app_db", "port": 5432}
     ]
   }
   ```

2. Get connection string from environment:
   - `DATABASE_URL` (full connection string)
   - Or construct from individual vars: `DB_HOST`, `DB_PORT`, `DB_USER`, etc.

3. Use test credentials (never production)

### Query Execution

For each verification query:

```python
# Pseudocode
query = "SELECT * FROM users WHERE email = $1"
params = ["jane@example.com"]

try:
    result = db.execute(query, params)
    return {
        "query": query,
        "params": params,
        "rows": result.rows,
        "row_count": len(result.rows),
        "success": True
    }
except Exception as e:
    return {
        "query": query,
        "error": str(e),
        "success": False
    }
```

### Verification Patterns

**After INSERT (POST):**
```json
{
  "action": "verify_insert",
  "query": "SELECT * FROM users WHERE id = $1",
  "params": ["uuid-123"],
  "expected": {"row_count": 1},
  "actual": {"row_count": 1},
  "passed": true
}
```

**After UPDATE (PUT/PATCH):**
```json
{
  "action": "verify_update",
  "query": "SELECT name FROM users WHERE id = $1",
  "params": ["uuid-123"],
  "expected": {"name": "Jane Updated"},
  "actual": {"name": "Jane Updated"},
  "passed": true
}
```

**After DELETE:**
```json
{
  "action": "verify_delete",
  "query": "SELECT * FROM users WHERE id = $1",
  "params": ["uuid-123"],
  "expected": {"row_count": 0},
  "actual": {"row_count": 0},
  "passed": true
}
```

### Constraint Testing

Test database constraints:

| Constraint | Test | Expected |
|------------|------|----------|
| Unique | Insert duplicate | Error (23505 in Postgres) |
| Not Null | Insert with NULL | Error |
| Foreign Key | Insert invalid ref | Error |
| Check | Insert invalid value | Error |

### Transaction Verification

For operations that should be atomic:

1. Start a transaction
2. Execute operation
3. Verify all changes or rollback
4. Check related tables

### Common Verification Queries

**User creation:**
```sql
SELECT id, email, created_at FROM users WHERE email = $1
-- Verify: row exists, created_at is recent
```

**Soft delete:**
```sql
SELECT id, deleted_at FROM users WHERE id = $1
-- Verify: deleted_at is set, data still exists
```

**Hard delete:**
```sql
SELECT COUNT(*) FROM users WHERE id = $1
-- Verify: count is 0
```

**Audit trail:**
```sql
SELECT action, entity_id, created_at FROM audit_logs
WHERE entity_type = 'user' AND entity_id = $1
ORDER BY created_at DESC LIMIT 1
-- Verify: action matches operation
```

### Redis Verification

For Redis operations:

```json
{
  "action": "verify_cache_set",
  "command": "GET user:uuid-123",
  "expected": {"exists": true},
  "actual": {"exists": true, "value": "{...}"},
  "passed": true
}
```

### MongoDB Verification

For MongoDB operations:

```json
{
  "action": "verify_document",
  "collection": "users",
  "query": {"email": "jane@example.com"},
  "expected": {"count": 1},
  "actual": {"count": 1, "doc": {...}},
  "passed": true
}
```

## Cleanup

After verification:
1. Roll back test data (if in transaction)
2. Or delete test records explicitly
3. Restore original state

Never leave test data in the database.

## Security Notes

1. Use parameterized queries (never string concatenation)
2. Use test/staging credentials only
3. Never log sensitive data (passwords, tokens)
4. Clean up test data after verification
