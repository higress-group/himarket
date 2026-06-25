# Backend Testing Standards

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## Testing

Testing effort should match the risk and scope of the change.

**Backend test expectations:**

- Service logic with business rules should have unit tests or focused Spring tests.
- Controller changes should be covered by request-level tests when they affect routes, HTTP methods, authorization, validation, or response shape.
- Repository queries with non-trivial predicates should have integration tests against a real or compatible database setup.
- Bug fixes should include a regression test when the scenario is deterministic.
- New async/event-driven behavior should test both event publication and listener side effects where practical.

**Minimum verification before PR:**

- Run targeted tests for the changed module when available.
- Run `./scripts/code-check.sh backend` or the relevant Maven command before submitting backend-only changes.
- If tests cannot be added or run, document the reason and the manual verification performed.

For backend-only cleanup or narrow service/controller changes, the minimum command set is:

```bash
git diff --check
./mvnw -q spotless:check -DskipTests
./mvnw -q -DskipTests test-compile
```

Run additional targeted tests when a change affects business rules, request validation, repository
predicates, transaction boundaries, domain events, or external integration adapters.
