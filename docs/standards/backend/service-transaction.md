# Backend Service, Error, Transaction, and Event Standards

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## Error Handling

### ErrorCode enum

Use existing error codes. Do not add new ones unless absolutely necessary:

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 4xx
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid parameter: {}"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request: {}"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication failed: {}"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied: {}"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found: {}:{}"),
    CONFLICT(HttpStatus.CONFLICT, "Resource conflict: {}"),

    // 5xx
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error: {}"),
    GATEWAY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Gateway error: {}"),
}
```

### Throwing exceptions

Always use `BusinessException` with an `ErrorCode`:

```java
// Resource not found -- use Resources constant for resource name
throw new BusinessException(ErrorCode.NOT_FOUND, Resources.PRODUCT, productId);

// Conflict -- resource already exists
throw new BusinessException(ErrorCode.CONFLICT,
        String.format("Product with name '%s' already exists", product.getName()));

// Invalid request -- business rule violation
throw new BusinessException(ErrorCode.INVALID_REQUEST, "API product already linked to API");
```

### Common patterns

```java
// find or throw NOT_FOUND
private Product findProduct(String productId) {
    return productRepository
            .findByProductIdAndAdminId(productId, contextHolder.getAdmin())
            .orElseThrow(() -> new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
}

// check duplicate then throw CONFLICT
productRepository.findByNameAndAdminId(param.getName(), contextHolder.getAdmin())
        .ifPresent(product -> {
            throw new BusinessException(ErrorCode.CONFLICT,
                    String.format("Product with name '%s' already exists", product.getName()));
        });
```

### Message formatting

- Use `String.format` for stable business exception templates.
- Use simple `+` only for short local strings where a template would be less readable.
- Use SLF4J `{}` placeholders for logs; do not use `String.format` in log messages.
- Use `String.join` or `Collectors.joining` for collection joining.

---


## Transaction Management

Keep transaction boundaries in the Service layer. Controllers should not start transactions, and
repositories should not contain business transaction decisions.

**Rules:**

- Use Spring `org.springframework.transaction.annotation.Transactional`.
- Do not use `jakarta.transaction.Transactional` or `javax.transaction.Transactional`.
- Use `@Transactional` for business services or methods that perform database writes.
- Use class-level `@Transactional` when most public methods mutate data and share the same boundary.
- Use method-level `@Transactional` when only selected methods mutate data or when different
  propagation/read-only settings are needed.
- Use `@Transactional(readOnly = true)` for read paths only when the class or method benefits from
  an explicit read-only boundary.
- Do not add `@Transactional` to stateless helpers, pure converters, configuration resolvers, or
  services that do not touch the database.
- Keep external side effects out of a transaction when possible. If a side effect depends on
  committed data, publish an event and handle it after commit.

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    public ProductResult createProduct(CreateProductParam param) { ... }

    @Transactional(readOnly = true)
    public ProductResult getProduct(String productId) { ... }
}
```

For mixed services, prefer method-level transactions:

```java
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    @Transactional
    public McpMetaResult saveMeta(SaveMcpMetaParam param) { ... }

    @Transactional(readOnly = true)
    public McpMetaResult getMeta(String mcpServerId) { ... }
}
```

---


## Event-Driven Patterns

Use Spring Events for cross-domain operations that should not block the main transaction.

### Define event

```java
@Getter
public class ProductDeletingEvent extends ApplicationEvent {

    private final String productId;

    public ProductDeletingEvent(String productId) {
        super(productId);
        this.productId = productId;
    }
}
```

### Publish event

```java
private final ApplicationEventPublisher eventPublisher;

@Override
public void deleteProduct(String productId) {
    Product product = findProduct(productId);
    productRepository.delete(product);

    eventPublisher.publishEvent(new ProductDeletingEvent(productId));
}
```

### Listen asynchronously

```java
@EventListener
@Async("taskExecutor")
public void onProductDeleting(ProductDeletingEvent event) {
    try {
        publicationRepository.deleteByProductId(event.getProductId());
        subscriptionRepository.deleteByProductId(event.getProductId());
        log.info("Product cleanup completed, productId={}", event.getProductId());
    } catch (Exception e) {
        log.warn(
                "Product cleanup failed, productId={}, errorMessage={}",
                event.getProductId(),
                e.getMessage(),
                e);
    }
}
```

---
