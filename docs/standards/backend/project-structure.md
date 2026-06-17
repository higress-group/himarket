# Backend Project Structure and Style

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## Project Architecture

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

| Module               | Contents                                                |
| -------------------- | ------------------------------------------------------- |
| `himarket-dal`       | Entity, Repository, AttributeConverter, enums           |
| `himarket-server`    | Controller, Service, DTO (Param/Result), core framework |
| `himarket-bootstrap` | Application entry point, Flyway migrations              |

**Base package:** `com.alibaba.himarket`

---


## Naming Conventions

### Class naming

| Layer                  | Suffix        | Example                        |
| ---------------------- | ------------- | ------------------------------ |
| Controller             | `Controller`  | `ProductController`            |
| Service interface      | `Service`     | `ProductService`               |
| Service implementation | `ServiceImpl` | `ProductServiceImpl`           |
| Repository             | `Repository`  | `ProductRepository`            |
| Entity                 | (none)        | `Product`                      |
| Input DTO              | `Param`       | `CreateProductParam`           |
| Output DTO             | `Result`      | `ProductResult`                |
| Event                  | `Event`       | `ProductDeletingEvent`         |
| Converter              | `Converter`   | `PortalSettingConfigConverter` |

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

| Item             | Rule                   | Example                        |
| ---------------- | ---------------------- | ------------------------------ |
| Request route    | lowercase with dash    | `/products`, `/mcp-servers`    |
| Database column  | `snake_case`           | `product_id`, `admin_id`       |
| Method parameter | `camelCase`            | `productId`, `categoryId`      |
| Enum             | `XxxStatus`, `XxxType` | `ProductStatus`, `ProductType` |

---


## Lombok Annotations

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
@Tag(
        name = "Product Management",
        description =
                "Product creation, update, publication, import, subscription, and category APIs")
@RestController
@RequestMapping("/products")
@Slf4j
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;
}
```

**Rules:**

- All data classes (DTO, Result, Entity) use `@Data` -- never hand-write getters/setters.
- When using `@Builder` on data classes that need framework construction or JSON
  serialization/deserialization, pair it with `@NoArgsConstructor` + `@AllArgsConstructor`.

**Recommendation:**

- Prefer Builder-based construction for new structured objects with multiple fields or optional
  fields. Setter-based code remains acceptable when updating existing objects, working with
  framework APIs, or when mutation is clearer than rebuilding the object.
- Use `@Builder.Default` for fields with default values.
- Entity classes extending `BaseEntity` add `@EqualsAndHashCode(callSuper = true)`.

---


## Annotation Order

Keep class-level annotations grouped in a stable order so the framework role, route or persistence
metadata, and Lombok-generated behavior are easy to scan.

**Controller classes:**

```java
@Tag(...)
@RestController
@RequestMapping("/products")
@Slf4j
@RequiredArgsConstructor
@Validated
public class ProductController {
}
```

**Service classes:**

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProductServiceImpl implements ProductService {
}
```

**Entity classes:**

```java
@Entity
@Table(name = "product")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseEntity {
}
```

**Rules:**

- Put framework and domain metadata first, then Lombok annotations.
- Keep `@Tag` before `@RestController` so OpenAPI grouping is visible before the web role.
- Put `@Transactional` at the end of service class annotations when using a class-level boundary.
- Do not repeatedly reorder annotations for style-only reasons once a class follows the project
  pattern.

---


## Dependency Injection

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


## Imports

Use explicit imports so dependency boundaries remain visible during review.

**Rules:**

- Do not use wildcard imports.
- Explicitly import third-party SDK model classes instead of hiding them behind wildcard imports.
- Do not introduce unused imports; run Spotless before submitting backend changes.

---


## Optional and Stream

### Optional rules

Use `Optional` only to express an absent value at a boundary.

**Recommended: Repository lookup boundary**

```java
Product product = productRepository
        .findByProductIdAndAdminId(productId, contextHolder.getAdmin())
        .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
```

**Recommended: short default chain**

```java
String protocol = Optional.ofNullable(meta)
        .map(McpMetadata::getProtocol)
        .orElse("SSE");
```

Do not use `Optional` for fields, method parameters, collection wrappers, or multi-step business
flows.

```java
// Avoid hiding parsing and side effects inside Optional chains.
Optional.ofNullable(productRef)
        .map(ProductRef::getMcpConfig)
        .map(config -> JsonUtil.parse(config, McpConfigResult.class))
        .ifPresent(config -> syncTools(config));
```

Use explicit branches for business flow:

```java
if (productRef == null || Strings.isBlank(productRef.getMcpConfig())) {
    return;
}

McpConfigResult mcpConfig = JsonUtil.parse(productRef.getMcpConfig(), McpConfigResult.class);
if (mcpConfig == null) {
    return;
}

syncTools(mcpConfig);
```

### Stream rules

Use streams for simple, linear, side-effect-free collection transformations.

```java
List<ProductResult> results = page.stream()
        .map(product -> new ProductResult().convertFrom(product))
        .toList();
```

```java
Map<String, ProductRef> productRefMap = productRefs.stream()
        .collect(Collectors.toMap(ProductRef::getProductId, Function.identity()));
```

When the collected list must be mutable, make the mutability explicit:

```java
List<ProductResult> results = new ArrayList<>(page.stream()
        .map(product -> new ProductResult().convertFrom(product))
        .toList());
```

Do not use stream pipelines for business flows that mix lookup, external calls, logging,
exception handling, or mutable assembly.

```java
// Avoid mixing database checks, logging, external calls, and mapping in one stream.
List<Result> results = products.stream()
        .filter(product -> repository.existsByProductId(product.getProductId()))
        .map(product -> {
            log.info("Processing product, productId={}", product.getProductId());
            return buildResult(product, gatewayClient.fetch(product.getProductId()));
        })
        .filter(Objects::nonNull)
        .toList();
```

Use an explicit loop instead:

```java
List<Result> results = new ArrayList<>();
for (Product product : products) {
    if (!repository.existsByProductId(product.getProductId())) {
        continue;
    }

    GatewayConfig config = gatewayClient.fetch(product.getProductId());
    Result result = buildResult(product, config);
    if (result != null) {
        results.add(result);
    }
}
```

Break complex collectors into named steps and loops when the collector hides conditional mapping or
intermediate state that may need debugging.

---


## Utilities and Readability

Prefer the JDK, Spring utilities, and existing project helpers over adding general-purpose utility
dependencies.

**Rules:**

- Use `Strings` for common string checks and equality helpers.
- Keep `Strings` small. Do not add formatting, joining, collection, or business-specific helpers to
  it.
- Use JDK `Base64`, `StandardCharsets`, `UUID`, and collection APIs for basic platform features.
- Use Spring helpers such as `CollectionUtils` and `DigestUtils` when they directly match the need.
- Use `JsonUtil` as the JSON entry point for application code.
- Use `IdGenerator` for generated business IDs.
- Prefer explicit mapping over broad bean-copy helpers unless the copy semantics are well defined.
- Do not add Hutool, Guava, or Apache Commons as broad replacements for basic JDK or Spring
  functionality.

Do not extract private methods just to make a method look short. Prefer clear local variables and
explicit branches first. Extract a method only when the logic is reused, the name adds meaningful
domain language, or the extraction significantly reduces complexity.

---
