# Server Testing Mixin

This mixin adds HTTP server testing capabilities to the verifier.

## When to Include

Include this mixin when:
- `app_type` is `server` or `hybrid`
- The project has HTTP routes or REST endpoints
- `environment.json` contains `startup` configuration

## Additional Context

You are testing an HTTP server. The application exposes REST endpoints.

## Server-Specific Protocol

### Starting the Server

1. Set environment variables from `test_environment.env_vars`
2. Run `start_command` in background with process group isolation
3. Poll readiness endpoint:
   - Try `GET /health` first
   - Fall back to first GET endpoint from task-specific scenarios
   - Fall back to TCP port check
4. Readiness timeout: 30 seconds
5. If not ready, capture stdout/stderr for debugging

### HTTP Request Execution

For each endpoint test:

```python
# Pseudocode for request execution
request = {
    "method": endpoint["method"],
    "url": f"http://localhost:{port}{endpoint['path']}",
    "headers": {
        "Content-Type": "application/json",
        **endpoint.get("headers", {})
    },
    "body": endpoint.get("body")
}

response = http_request(request)

assertions = []
if "status" in endpoint:
    assertions.append(check_status(response, endpoint["status"]))
if "body_contains" in endpoint:
    assertions.append(check_body_contains(response, endpoint["body_contains"]))
if "json_path" in endpoint:
    for path_check in endpoint["json_path"]:
        assertions.append(check_json_path(response, path_check))
```

### Common Assertions

**Status Code:**
```json
{"type": "status_code", "expected": 200, "actual": 200, "passed": true}
```

**Body Contains:**
```json
{"type": "body_contains", "expected": "success", "found": true, "passed": true}
```

**JSON Path:**
```json
{
  "type": "json_path",
  "path": "$.data.id",
  "operator": "exists",
  "passed": true
}
```

### Side Effect Verification

For POST/PUT/DELETE requests, verify the change persisted:

1. **Direct DB query** (if DB access available):
   ```sql
   SELECT * FROM users WHERE email = 'jane@example.com'
   ```

2. **API query** (preferred):
   ```
   POST /api/users → 201 {id: "uuid-123"}
   GET /api/users/uuid-123 → 200 {id: "uuid-123", name: "Jane Doe"}
   ```

3. **Report side effect verification:**
   ```json
   {
     "side_effect": "user_created",
     "verified_by": "GET /api/users/uuid-123 returned 200",
     "passed": true
   }
   ```

### Error Testing

Test error responses for complete coverage:

| Scenario | Expected |
|----------|----------|
| Missing required field | 400 Bad Request |
| Invalid format (e.g., bad email) | 400 Bad Request |
| Duplicate unique field | 409 Conflict |
| Resource not found | 404 Not Found |
| Unauthorized access | 401 Unauthorized |
| Forbidden action | 403 Forbidden |

### Server Shutdown

1. Send SIGTERM to process group
2. Wait up to 10 seconds for graceful shutdown
3. If still running, send SIGKILL
4. Verify process is terminated
5. Record shutdown behavior in report

## Framework-Specific Notes

### REST API Patterns
- Use `Content-Type: application/json` for JSON bodies
- Include `Authorization: Bearer {token}` for authenticated endpoints
- Check `Location` header on 201 responses

### GraphQL
- All requests are POST to `/graphql`
- Body contains `query` and `variables`
- Check `data` and `errors` in response

### WebSocket
- Initial connection via HTTP upgrade
- Use WS protocol for subsequent messages
- Test connection lifecycle: open → messages → close
