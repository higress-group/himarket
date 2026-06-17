# Backend Security, Comments, and Logging Standards

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## Comments and Logging

### Language

All comments and log messages must be in **English**.

### JavaDoc

Public APIs, service interfaces, extension points, and non-obvious public/protected methods must
have JavaDoc comments. Simple Controller handlers and straightforward Service implementation
methods do not need JavaDoc when `@Operation` or the method name already communicates the behavior.
Follow standard JavaDoc format with `@param`, `@return`, `@throws` tags when they add useful
information.

```java
/**
 * Lists products visible to the current portal or administrator.
 *
 * @param param query filters
 * @param pageable pagination and sorting parameters
 * @return matching products
 */
PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable);
```

### JavaDoc format

Always use multi-line JavaDoc format:

```java
/**
 * Create a new product.
 *
 * @param param the creation parameters
 * @return the created product result
 */
public ProductResult createProduct(CreateProductParam param) { ... }
```

Never use single-line format:

```java
/** Create a new product. */
public ProductResult createProduct(CreateProductParam param) { ... }
```

Even for short comments, use multi-line format for consistency:

```java
// Correct
/**
 * ADMIN / GATEWAY / NACOS.
 */
private String origin;

// Incorrect
/** ADMIN / GATEWAY / NACOS. */
private String origin;
```

Do not write comments that merely restate the field or method name. Comments should explain what the name does not convey:

```java
// Bad: restates the name
/** The product name. */
private String name;

// Good: explains constraints or valid values
/**
 * Must be unique within the same admin. Max 50 characters.
 */
private String name;

// Good: no comment needed when the name is self-explanatory
private String name;
```

### Logging

Use `@Slf4j` with `{}` placeholders. Place the exception object as the last argument:

```java
log.error(
        "Failed to update product source, productId={}, errorType={}, errorMessage={}",
        productId,
        e.getClass().getSimpleName(),
        e.getMessage(),
        e);
```

Keep log messages concise, meaningful, and stable. Use natural English sentences rather than
message-code style strings.

**Rules:**

- Use SLF4J placeholders; do not build log messages with string concatenation.
- Include stable identifiers and state that help troubleshooting, such as `productId`, `gatewayId`,
  `operation`, `status`, `errorType`, and `errorMessage`.
- For external dependency calls, include `dependency`, `operation`, and status or result fields
  when available.
- Keep the exception object as the final argument when logging failures.
- Use `debug` for detailed diagnostic flow, `info` for important state transitions, `warn` for
  recoverable failures, and `error` for failed operations that need attention.
- Do not log secrets or credential payloads. Non-secret values that are useful for troubleshooting
  should remain visible rather than being replaced with lossy derived values.
- Do not use `System.out`, `System.err`, or `printStackTrace`.

```java
log.warn(
        "Gateway request returned non-success status, dependency=Higress, operation=createRoute,"
                + " gatewayId={}, status={}, errorMessage={}",
        gatewayId,
        response.getStatusCode(),
        response.getBody());
```

```java
log.info(
        "Product configuration reload requested, productId={}, sourceType={}",
        productId,
        sourceType);
```

Avoid these patterns:

```java
log.error("Failed to update product source: " + productId, e);
log.warn("product.config.parse.failed, productId={}", productId);
log.error("Failed to update product source, productId={}, errorMessage={}",
        productId, e.getMessage());
```

---


## Security and Sensitive Data

HiMarket handles credentials, tokens, API keys, gateway configs, and Nacos connection metadata. Treat these as sensitive by default.

**Rules:**

- Never log full tokens, API keys, HMAC secrets, passwords, authorization headers, or credential payloads.
- When a sensitive identifier is needed for debugging, log only a masked value or a stable non-secret ID.
- Do not return internal secrets in Result DTOs. If a field is needed for display, return a masked value.
- Do not include sensitive values in exception messages, validation messages, event payloads, or cache keys that may be logged.
- Prefer explicit allowlists when converting Entity to Result; avoid exposing new entity fields accidentally.

```java
// Good: log stable non-secret identifiers
log.info("Credential created for consumer: {}", consumerId);

// Bad: leaks secret material
log.info("Credential created: {}", credential);
```
