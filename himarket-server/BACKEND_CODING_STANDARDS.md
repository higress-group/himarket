# HiMarket Backend Coding Standards

`ProductServiceImpl` is the gold-standard reference for code style -- when in doubt, follow its patterns.

## Table of Contents

1. [Project Architecture](#1-project-architecture)
2. [Naming Conventions](#2-naming-conventions)
3. [Lombok Annotations](#3-lombok-annotations)
4. [Dependency Injection](#4-dependency-injection)
5. [InputConverter / OutputConverter](#5-inputconverter--outputconverter)
6. [Error Handling](#6-error-handling)
7. [Comments and Logging](#7-comments-and-logging)
8. [Optional and Stream](#8-optional-and-stream)
9. [Pagination](#9-pagination)
10. [Controller Layer](#10-controller-layer)
11. [Event-Driven Patterns](#11-event-driven-patterns)
12. [Checklist](#12-checklist)
13. [Reference Files](#13-reference-files)

---

## 1. Project Architecture

Layered Spring Boot architecture with strict unidirectional dependencies:

```
Controller -> Service (Interface + Impl) -> Repository -> Entity
     |                                                        |
  Param (InputConverter)                          Result (OutputConverter)
```

**Module boundaries:**

```
himarket-dal (data layer) <- himarket-server (business layer) <- himarket-bootstrap (bootstrap)
```

| Module | Contents |
|--------|----------|
| `himarket-dal` | Entity, Repository, AttributeConverter, enums |
| `himarket-server` | Controller, Service, DTO (Param/Result), core framework |
| `himarket-bootstrap` | Application entry point, Flyway migrations |

**Base package:** `com.alibaba.himarket`

---

## 2. Naming Conventions

### Class naming

| Layer | Suffix | Example |
|-------|--------|---------|
| Controller | `Controller` | `ProductController` |
| Service interface | `Service` | `ProductService` |
| Service implementation | `ServiceImpl` | `ProductServiceImpl` |
| Repository | `Repository` | `ProductRepository` |
| Entity | (none) | `Product` |
| Input DTO | `Param` | `CreateProductParam` |
| Output DTO | `Result` | `ProductResult` |
| Event | `Event` | `ProductDeletingEvent` |
| Converter | `Converter` | `PortalSettingConfigConverter` |

**Param patterns:**
- `Create[Entity]Param` -- creation
- `Update[Entity]Param` -- updates
- `Query[Entity]Param` -- queries / filtering

**Result pattern:** `[Entity]Result`

### Method naming

Use `verb + noun`. Key verbs:
- `create` / `delete` -- CRUD operations
- `get` -- single entity retrieval
- `list` -- collection retrieval
- `update` -- modifications
- `exists` -- existence checks
- `find` -- internal lookup methods (private)

### Other conventions

| Item | Rule | Example |
|------|------|---------|
| Request route | lowercase with dash | `/products`, `/mcp-servers` |
| Database column | `snake_case` | `product_id`, `admin_id` |
| Method parameter | `camelCase` | `productId`, `categoryId` |
| Enum | `XxxStatus`, `XxxType` | `ProductStatus`, `ProductType` |

---

## 3. Lombok Annotations

### Entity classes

```java
@Entity
@Table(name = "product", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id"}, name = "uk_product_id")
})
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    @Column(name = "status", length = 32)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProductStatus status = ProductStatus.PENDING;
}
```

### Param classes (Input DTO)

```java
@Data
public class CreateProductParam implements InputConverter<Product> {

    @NotBlank(message = "API product name cannot be blank")
    @Size(max = 50, message = "API product name cannot exceed 50 characters")
    private String name;

    @Size(max = 256, message = "API product description cannot exceed 256 characters")
    private String description;

    @NotNull(message = "API product type cannot be null")
    private ProductType type;
}
```

### Result classes (Output DTO)

```java
@Data
public class ProductResult implements OutputConverter<ProductResult, Product> {

    private String productId;
    private String name;
    private String description;
    private ProductStatus status;
    private ProductType type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### Service implementation

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {

    private final ContextHolder contextHolder;
    private final ProductRepository productRepository;
}
```

### Controller

```java
@Tag(name = "API Product Management")
@RestController
@RequestMapping("/products")
@RamResource("product")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
}
```

**Rules:**
- All data classes (DTO, Result, Entity) use `@Data` -- never hand-write getters/setters.
- Prefer `@Builder` with `@NoArgsConstructor` + `@AllArgsConstructor`.
- Use `@Builder.Default` for fields with default values.
- Entity classes extending `BaseEntity` add `@EqualsAndHashCode(callSuper = true)`.

---

## 4. Dependency Injection

Constructor injection via Lombok only. Never use `@Autowired`.

```java
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ContextHolder contextHolder;      // all fields are final
    private final ProductRepository productRepository;
}
```

**Rules:**
- Every injected dependency is declared `final`.
- The class has `@RequiredArgsConstructor` which generates the constructor.
- Never use `@Autowired` annotation.

---

## 5. InputConverter / OutputConverter

### InputConverter -- Param to Entity

```java
// Creating a new entity
Product product = param.convertTo();
product.setProductId(productId);
product.setAdminId(contextHolder.getAdmin());
productRepository.save(product);

// Updating an existing entity
Product product = findProduct(productId);
param.update(product);
productRepository.saveAndFlush(product);
```

### OutputConverter -- Entity to Result

```java
// Single entity conversion
ProductResult result = new ProductResult().convertFrom(product);

// Collection conversion
List<ProductResult> results = page.stream()
        .map(product -> new ProductResult().convertFrom(product))
        .collect(Collectors.toList());

// Optional with map
return productRefRepository
        .findFirstByProductId(productId)
        .map(ref -> new ProductRefResult().convertFrom(ref))
        .orElse(null);
```

---

## 6. Error Handling

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
        StrUtil.format("Product with name '{}' already exists", product.getName()));

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
                    StrUtil.format("Product with name '{}' already exists", product.getName()));
        });
```

---

## 7. Comments and Logging

### Language

All comments and log messages must be in **English**.

### JavaDoc

All public and protected methods must have JavaDoc comments. Follow standard JavaDoc format with `@param`, `@return`, `@throws` tags. Keep descriptions concise.

```java
/**
 * Fill product details, including product categories and product reference config.
 *
 * @param products the list of products to fill
 */
private void fillProducts(List<ProductResult> products) { ... }
```

### Logging

Use `@Slf4j` with `{}` placeholders. Place the exception object as the last argument:

```java
// Info -- operation completed
log.info("Auto-sync product ref: {} successfully completed", productId);

// Warn -- non-critical issues
log.warn("Failed to parse modelConfig for product: {}", productRef.getProductId(), e);

// Error -- failures with exception
log.error("Failed to get portal: {}", publication.getPortalId(), e);
```

Keep log messages concise and meaningful.

---

## 8. Optional and Stream

### Optional patterns

```java
// orElseThrow for mandatory lookups
Product product = productRepository
        .findByProductIdAndAdminId(productId, contextHolder.getAdmin())
        .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));

// ifPresent for conditional logic
productRefRepository.findByProductId(productId)
        .ifPresent(ref -> { ... });

// map for transformation
return productRefRepository.findFirstByProductId(productId)
        .map(ref -> new ProductRefResult().convertFrom(ref))
        .orElse(null);
```

### Stream patterns

```java
// map + collect for list transformation
List<ProductResult> results = page.stream()
        .map(product -> new ProductResult().convertFrom(product))
        .collect(Collectors.toList());

// toMap for lookup maps
Map<String, ProductRef> productRefMap = productRefRepository
        .findByProductIdIn(productIds).stream()
        .collect(Collectors.toMap(ProductRef::getProductId, ref -> ref));

// filter for conditional collection
List<String> notFoundIds = productIds.stream()
        .filter(id -> !existedIds.contains(id))
        .collect(Collectors.toList());
```

Do not over-chain streams to the point where readability suffers.

---

## 9. Pagination

### PageResult construction

```java
// Preferred: direct conversion
return new PageResult<ProductResult>()
        .convertFrom(page, product -> new ProductResult().convertFrom(product));
```

Use `PageResult.of()` only when post-processing is required:

```java
// Use when secondary processing is needed
List<ProductResult> results = page.stream()
        .map(product -> new ProductResult().convertFrom(product))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
return PageResult.of(results, page.getNumber() + 1, page.getSize(), page.getTotalElements());
```

**Rules:**
- Prefer `convertFrom` for simple entity-to-result mapping.
- Use `of` when filtering, sorting, or additional data manipulation is needed.
- `convertFrom` automatically handles page number conversion (Spring uses 0-based, frontend uses 1-based).

### Controller pagination

```java
@GetMapping
public PageResult<ProductResult> listProducts(
        QueryProductParam param, Pageable pageable) {
    return productService.listProducts(param, pageable);
}
```

`Pageable` is automatically resolved from query parameters: `?page=0&size=20&sort=name,desc`.

---

## 10. Controller Layer

```java
@Tag(name = "API Product Management")
@RestController
@RequestMapping("/products")
@RamResource("product")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Create API product")
    @PostMapping
    @AdminAuth
    public ProductResult createProduct(@RequestBody @Valid CreateProductParam param) {
        return productService.createProduct(param);
    }

    @Operation(summary = "List API products")
    @GetMapping
    public PageResult<ProductResult> listProducts(
            QueryProductParam param, Pageable pageable) {
        return productService.listProducts(param, pageable);
    }

    @Operation(summary = "Get API product details")
    @GetMapping("/{productId}")
    public ProductResult getProduct(@PathVariable String productId) {
        return productService.getProduct(productId);
    }

    @Operation(summary = "Update API product")
    @PutMapping("/{productId}")
    @AdminAuth
    public ProductResult updateProduct(
            @PathVariable String productId, @RequestBody @Valid UpdateProductParam param) {
        return productService.updateProduct(productId, param);
    }

    @Operation(summary = "Delete API product")
    @DeleteMapping("/{productId}")
    @AdminAuth
    public void deleteProduct(@PathVariable String productId) {
        productService.deleteProduct(productId);
    }
}
```

**Rules:**
- Use `@Tag` and `@Operation` for Swagger/OpenAPI documentation.
- POST/PUT body params use `@RequestBody @Valid`.
- Path params use `@PathVariable`.
- Query/filter params: bind directly to Param object (no annotation needed for GET).
- Pagination: accept `Pageable` parameter.
- Authorization: `@AdminAuth`, `@AdminOrDeveloperAuth`, `@DeveloperAuth`, `@PublicAccess`.
- Controller methods are thin -- delegate all logic to the Service layer.

---

## 11. Event-Driven Patterns

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
@Override
public void deleteProduct(String productId) {
    Product product = findProduct(productId);
    productRepository.delete(product);

    SpringUtil.publishEvent(new ProductDeletingEvent(productId));
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
        log.info("Cleanup completed for product {}", event.getProductId());
    } catch (Exception e) {
        log.warn("Cleanup failed for product {}: {}", event.getProductId(), e.getMessage());
    }
}
```

---

## 12. Checklist

### Must do

- [ ] Service classes add `@Service`, `@Slf4j`, `@RequiredArgsConstructor`, `@Transactional`
- [ ] All dependencies declared as `private final`
- [ ] Entity extends `BaseEntity` with Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- [ ] DTO implements `InputConverter<T>` or `OutputConverter<Self, T>`
- [ ] Param DTO has Jakarta Validation annotations on fields
- [ ] Controller methods return business objects, never manually wrap response
- [ ] Business errors throw `BusinessException(ErrorCode.XXX, ...)`
- [ ] ID generation uses `IdGenerator` utility
- [ ] Sensitive operations are logged (info / warn / error)
- [ ] Complex deletions publish domain events

### Must not

- [ ] Do not manually create `Response` wrapper objects (`ResponseAdvice` handles it)
- [ ] Do not use `null` as a business error return value (throw exception)
- [ ] Do not mix transaction concerns in Service (use `@Transactional`)
- [ ] Do not put business logic in Entity (data container only)
- [ ] Do not cross module boundaries (`dal` -> `server` -> `bootstrap` only)
- [ ] Do not use `@Autowired` annotation
- [ ] Do not hand-write getters / setters / constructors where Lombok can do it

---

## 13. Reference Files

| File | Path |
|------|------|
| ProductServiceImpl (gold standard) | `himarket-server/src/main/java/com/alibaba/himarket/service/impl/ProductServiceImpl.java` |
| ProductController | `himarket-server/src/main/java/com/alibaba/himarket/controller/ProductController.java` |
| InputConverter | `himarket-server/src/main/java/com/alibaba/himarket/dto/converter/InputConverter.java` |
| OutputConverter | `himarket-server/src/main/java/com/alibaba/himarket/dto/converter/OutputConverter.java` |
| ErrorCode | `himarket-server/src/main/java/com/alibaba/himarket/core/exception/ErrorCode.java` |
| BusinessException | `himarket-server/src/main/java/com/alibaba/himarket/core/exception/BusinessException.java` |
| CreateProductParam | `himarket-server/src/main/java/com/alibaba/himarket/dto/params/product/CreateProductParam.java` |
| ProductResult | `himarket-server/src/main/java/com/alibaba/himarket/dto/result/product/ProductResult.java` |
| Product entity | `himarket-dal/src/main/java/com/alibaba/himarket/entity/Product.java` |
| PageResult | `himarket-server/src/main/java/com/alibaba/himarket/dto/result/common/PageResult.java` |
| ContextHolder | `himarket-server/src/main/java/com/alibaba/himarket/core/security/ContextHolder.java` |
| IdGenerator | `himarket-server/src/main/java/com/alibaba/himarket/core/utils/IdGenerator.java` |
