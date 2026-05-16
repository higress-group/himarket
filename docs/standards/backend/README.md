# HiMarket Backend Coding Standards

Use this document as the backend standards entry point. Detailed rules live in focused topic
documents in the same directory.

## Scope

These standards apply to new backend code and backend code being actively modified. Existing code
that does not fully comply should be treated as cleanup debt, not as a reason to copy the old
pattern.

Use existing code in the same layer or module as the primary reference. For product-domain business
orchestration, `ProductServiceImpl` is a useful reference, but do not copy its complexity when a
smaller, more focused implementation is sufficient.

## Topic Documents

| Topic | Document |
| ----- | -------- |
| Project architecture, naming, Lombok, dependency injection, Optional, Stream | [project-structure.md](project-structure.md) |
| Maven and POM dependency management | [dependency-management.md](dependency-management.md) |
| DTO conversion, `InputConverter`, `OutputConverter`, `JsonUtil`, polymorphic JSON | [dto-json.md](dto-json.md) |
| Controller, OpenAPI, RESTful routes, pagination | [api-controller.md](api-controller.md) |
| Service layer, business errors, transactions, domain events | [service-transaction.md](service-transaction.md) |
| Entity mapping, database schema, Flyway migrations | [data-flyway.md](data-flyway.md) |
| English comments, JavaDoc, logging, sensitive data | [security-logging.md](security-logging.md) |
| Backend testing expectations | [testing.md](testing.md) |

## Core Architecture Rules

- Keep the module dependency direction strict:

```text
himarket-dal (data layer) <- himarket-server (business layer) <- himarket-bootstrap (bootstrap)
```

- Keep Controller methods thin. Delegate business logic to the Service layer.
- Keep transaction boundaries in the Service layer.
- Keep Entity classes as persistence data containers. Do not put business logic in Entity code.
- Use Spring Events for cross-domain cleanup or side effects that should not tightly couple modules.

## Core Implementation Rules

- Business Service classes use `@Service`, `@Slf4j`, and `@RequiredArgsConstructor`.
- Injected dependencies are `private final`; do not use `@Autowired`.
- Data classes use Lombok. Do not hand-write getters, setters, or constructors when Lombok covers
  the need.
- Param DTOs implement `InputConverter<T>` when converting into entities.
- Result DTOs implement `OutputConverter<Self, T>` when converting from entities.
- JSON serialization and deserialization must go through `JsonUtil`.
- Business errors must throw `BusinessException(ErrorCode.XXX, ...)`.
- Do not return `null` to represent business errors.
- Controller methods return business objects for ordinary JSON APIs. Do not manually create unified
  response wrappers; `ResponseAdvice` handles that.
- Use `IdGenerator` for generated business IDs.

## Core API Rules

- Every Controller has `@Tag(name = "...", description = "...")`.
- Every request handler has `@Operation(summary = "...")`.
- OpenAPI text must be in English.
- Use plural resource nouns and kebab-case paths, for example `/products` and `/mcp-servers`.
- Avoid verbs in URLs when a clear resource-oriented route exists.
- Use explicit special response annotations only for raw responses such as SSE, file downloads,
  redirects, binary content, or endpoints intentionally bypassing the unified wrapper.

## Core Data Rules

- Flyway migration scripts live in `himarket-bootstrap/src/main/resources/db/migration/`.
- Do not modify a migration script that has been released or applied in a shared environment.
- Local-only, unreleased migration scripts may be merged or renumbered before sharing.
- Migration SQL should be idempotent where practical and safe to re-run in local or test
  environments.
- Entity mappings and migration scripts must evolve together.

## Core Dependency Rules

- Third-party dependency versions are managed in the parent `pom.xml` through `<properties>` and
  `<dependencyManagement>`.
- Maven plugin versions are managed in the parent `pom.xml` through `<properties>` and
  `<pluginManagement>`.
- Submodule POM files should not declare versions for dependencies managed by the parent POM.
- Existing direct dependency versions in module POM files are cleanup debt and should not be copied.

## Core Security and Logging Rules

- All comments and log messages must be in English.
- Never log full tokens, API keys, HMAC secrets, passwords, authorization headers, or credential
  payloads.
- Do not include sensitive values in exception messages, validation messages, event payloads, or
  cache keys that may be logged.
- Use `@Slf4j` with `{}` placeholders. Pass exception objects as the last log argument.

## Recommended Practices

- Prefer Builder-based construction for new DTO, Result, Entity, config, or metadata objects with
  multiple fields or optional fields. Setter-based code is still acceptable for simple updates,
  framework-owned objects, JPA-managed entities, and cases where mutation is clearer than rebuilding
  the object.

## Checklist

### Must Do

- [ ] Service classes add `@Service`, `@Slf4j`, and `@RequiredArgsConstructor`
- [ ] Database write operations have an explicit Service-layer transaction boundary
- [ ] New dependency versions are managed in the parent POM through `<properties>` and
      `<dependencyManagement>`
- [ ] All injected dependencies are declared as `private final`
- [ ] Entity extends `BaseEntity` with Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, and
      `@AllArgsConstructor`
- [ ] DTO implements `InputConverter<T>` or `OutputConverter<Self, T>` when conversion is needed
- [ ] Param DTO has Jakarta Validation annotations on fields
- [ ] Ordinary JSON Controller methods return business objects, never manually wrapped responses
- [ ] Business errors throw `BusinessException(ErrorCode.XXX, ...)`
- [ ] JSON serialization and deserialization use `JsonUtil`
- [ ] Sensitive values are never logged or returned unintentionally
- [ ] Tests or documented verification cover behavior changes
- [ ] ID generation uses `IdGenerator`
- [ ] State-changing operations log stable non-secret identifiers when useful
- [ ] Complex deletions publish domain events when cross-domain cleanup is needed

### Must Not

- [ ] Do not manually create unified response wrapper objects
- [ ] Do not declare third-party dependency versions directly in module POM files when they can be
      managed by the parent POM
- [ ] Do not use `null` as a business error return value
- [ ] Do not start transactions in Controller or Repository code
- [ ] Do not modify released or shared Flyway migration scripts
- [ ] Do not instantiate `ObjectMapper` directly in business code
- [ ] Do not parse polymorphic JSON before the required type discriminator is present
- [ ] Do not put business logic in Entity classes
- [ ] Do not cross module boundaries against the dependency direction
- [ ] Do not use `@Autowired`

## Reference Files

| File | Path |
| ---- | ---- |
| Parent POM | `pom.xml` |
| ProductServiceImpl reference | `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ProductServiceImpl.java` |
| ProductController | `himarket-server/src/main/java/com/alibaba/himarket/controller/ProductController.java` |
| InputConverter | `himarket-server/src/main/java/com/alibaba/himarket/dto/converter/InputConverter.java` |
| OutputConverter | `himarket-server/src/main/java/com/alibaba/himarket/dto/converter/OutputConverter.java` |
| ErrorCode | `himarket-server/src/main/java/com/alibaba/himarket/core/exception/ErrorCode.java` |
| BusinessException | `himarket-server/src/main/java/com/alibaba/himarket/core/exception/BusinessException.java` |
| Product entity | `himarket-dal/src/main/java/com/alibaba/himarket/entity/Product.java` |
| PageResult | `himarket-server/src/main/java/com/alibaba/himarket/dto/result/common/PageResult.java` |
| ContextHolder | `himarket-server/src/main/java/com/alibaba/himarket/core/security/ContextHolder.java` |
| IdGenerator | `himarket-server/src/main/java/com/alibaba/himarket/core/utils/IdGenerator.java` |
| JsonUtil | `himarket-dal/src/main/java/com/alibaba/himarket/utils/JsonUtil.java` |
| Flyway migrations | `himarket-bootstrap/src/main/resources/db/migration/` |
