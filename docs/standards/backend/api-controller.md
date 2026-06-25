# Backend API and Controller Standards

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## Pagination

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
        .toList();
return PageResult.of(results, page.getNumber() + 1, page.getSize(), page.getTotalElements());
```

**Rules:**

- Prefer `convertFrom` for simple entity-to-result mapping.
- Use `of` when filtering, sorting, or additional data manipulation is needed.
- `PageResult` responses use 1-based page numbers.
- Spring `Pageable` request parameters are 0-based by default unless explicitly adapted.
- Endpoints with explicit `page` / `size` request parameters should use 1-based page numbers and
  convert to external or database paging semantics at the service/integration boundary.

### Controller pagination

```java
@Operation(summary = "List products")
@GetMapping
public PageResult<ProductResult> listProducts(
        QueryProductParam param, Pageable pageable) {
    return productService.listProducts(param, pageable);
}
```

`Pageable` is automatically resolved from Spring query parameters, which are 0-based by default:
`?page=0&size=20&sort=name,desc`. `PageResult.convertFrom(Page, ...)` converts the response page
number to 1-based.

---


## Controller Layer

```java
@Tag(
        name = "Product Management",
        description =
                "Product creation, update, publication, import, subscription, and category APIs")
@RestController
@RequestMapping("/products")
@Slf4j
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Create product")
    @PostMapping
    @AdminAuth
    public ProductResult createProduct(@RequestBody @Valid CreateProductParam param) {
        return productService.createProduct(param);
    }

    @Operation(summary = "List products")
    @GetMapping
    @PublicAccess
    public PageResult<ProductResult> listProducts(
            QueryProductParam param, Pageable pageable) {
        return productService.listProducts(param, pageable);
    }

    @Operation(summary = "Get product")
    @GetMapping("/{productId}")
    @PublicAccess
    public ProductResult getProduct(@PathVariable String productId) {
        return productService.getProduct(productId);
    }

    @Operation(summary = "Update product")
    @PutMapping("/{productId}")
    @AdminAuth
    public ProductResult updateProduct(
            @PathVariable String productId, @RequestBody @Valid UpdateProductParam param) {
        return productService.updateProduct(productId, param);
    }

    @Operation(summary = "Delete product")
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
- Add `@Validated` to the Controller when method parameters use Jakarta Validation constraints.
- Path params use `@PathVariable`.
- Query/filter params: bind directly to Param object (no annotation needed for GET).
- Pagination: prefer `Pageable` for database-backed lists; use explicit `page` / `size` when
  integrating with APIs that require 1-based paging.
- Authorization: `@AdminAuth`, `@AdminOrDeveloperAuth`, `@DeveloperAuth`, `@PublicAccess`.
- Controller methods are thin -- delegate business logic to the Service layer.
- Ordinary JSON endpoints return business objects. Raw responses such as SSE, file downloads,
  redirects, or `ResponseEntity` are allowed only when the response shape requires it.

### Request validation

Use Jakarta Validation at the HTTP boundary so invalid input is rejected before it reaches the
Service layer.

```java
@PostMapping
public ProductResult createProduct(@RequestBody @Valid CreateProductParam param) {
    return productService.createProduct(param);
}
```

Use class-level `@Validated` when validating `@PathVariable` or `@RequestParam` values:

```java
@RestController
@RequestMapping("/mcp-servers")
@Validated
public class McpServerController {

    @GetMapping("/meta/batch")
    public List<McpMetaResult> listMetaByProductIds(
            @RequestParam
                    List<@NotBlank(message = "Product ID cannot be blank") String> productIds) {
        return mcpServerService.listMetaByProductIds(productIds);
    }
}
```

**Rules:**

- Request body DTOs use `@Valid`.
- Collection fields or parameters validate both the collection and its elements when blank elements
  are invalid.
- Use `@NotBlank` for strings, `@NotNull` for required objects and enums, and `@NotEmpty` for
  required collections.
- Validation messages must be concise English and name the invalid field or value.
- Raw string bodies, file uploads, SSE, redirects, and downloads may bypass DTO validation when the
  response or request shape requires it.

### OpenAPI documentation

All OpenAPI text must be in English. Keep the documentation useful and consistent rather than
verbose.

**Controller tag:**

- Every Controller must define `@Tag(name = "...", description = "...")`.
- Place `@Tag` immediately before `@RestController`.
- Use `Management` for admin or operational resources, `Authentication` for auth flows, and
  `API` for public integration surfaces.
- The tag description should describe the resource group, not repeat the class name.
- Keep tag names stable. Renaming a tag changes the generated documentation grouping and should be
  reviewed like an API documentation change.

```java
@Tag(
        name = "Product Management",
        description =
                "Product creation, update, publication, import, subscription, and category APIs")
@RestController
@RequestMapping("/products")
public class ProductController { ... }
```

**Operation annotation:**

- Every request handler must have `@Operation(summary = "...")`.
- Place `@Operation` immediately before the Spring mapping annotation.
- Ordinary CRUD operations should use a concise summary only.
- Write summaries as short verb phrases such as `Create product`, `List MCP server endpoints`, or
  `Refresh MCP tool list`.
- Add `description` only when the behavior is not obvious from the route and method, such as
  import, refresh, deployment, authentication redirects, streaming responses, non-wrapper
  responses, or cross-system queries.
- Do not describe DTO fields in the operation description; put field-level details on the DTO
  schema.

```java
@Operation(summary = "List products")
@GetMapping
public PageResult<ProductResult> listProducts(QueryProductParam param, Pageable pageable) { ... }

@Operation(
        summary = "Import product resources",
        description = "Import resources from a gateway, Nacos, or an external marketplace")
@PostMapping("/import")
public ImportProductsResult importProducts(@RequestBody @Valid ImportProductsParam param) { ... }
```

**Responses and media types:**

- Do not add repetitive `@ApiResponse(responseCode = "200")` to ordinary JSON APIs.
  `ResponseAdvice` and `SwaggerConfig` document the unified response wrapper and common error
  responses.
- Add explicit `@ApiResponse` only for raw or special responses: SSE streams, file downloads,
  redirects, binary content, or endpoints intentionally bypassing the unified wrapper.
- For raw responses, document the media type and schema so generated OpenAPI clients do not assume
  the normal JSON wrapper.
- For file uploads, document the multipart file parameter with `@Parameter`.

**Parameters and schemas:**

- Prefer `@Schema` on Param/Result DTO fields for request and response body documentation.
- Add `@Parameter` only for multipart files or path/query parameters whose meaning is not obvious
  from the method and parameter name.
- Do not duplicate DTO field descriptions in Controller annotations.
- Use `requiredMode = Schema.RequiredMode.REQUIRED` when a field is required by the API contract,
  especially when the requirement is not obvious from Java type nullability.
- For polymorphic or shape-dependent request bodies, document the discriminator and provide examples
  on the request body or DTO schema.

### RESTful API conventions

**Resource naming:**

- Use plural nouns for resource collections: `/products`, `/api-definitions`
- Use kebab-case for multi-word resources: `/sandbox-deployments`, `/mcp-servers`
- Nested resources express ownership: `/api-definitions/{id}/deployments`

**HTTP methods:**

- `GET` -- read / query (never modify state)
- `POST` -- create a resource or trigger an action
- `PUT` -- full update of a resource
- `DELETE` -- remove a resource

**Avoid verbs in URLs:**

```java
// Bad: verb in URL
@PostMapping("/{id}/deploy")
@PutMapping("/sandbox-deployments/{id}/stop")

// Good: resource-oriented
@PostMapping("/{id}/deployments")                    // create a deployment
@DeleteMapping("/sandbox-deployments/{id}")         // delete = stop
```

**Action endpoints** -- when an operation doesn't map naturally to CRUD, prefer the clearest resource-oriented command path. Do not force an artificial resource name just to avoid verbs.

```java
// Deploy is a creation action on deployments
@PostMapping("/{id}/deployments")

// Command on a concrete sub-resource
@PostMapping("/{id}/configurations/reload")

// Product source is a sub-resource; implementation details stay in the body
@PutMapping("/{id}/source")
```

Use `/actions/...` only when there is no clear domain sub-resource and the action would otherwise make the URL misleading.

**Status changes** -- prefer `PATCH` for partial updates, or model status as a sub-resource:

```java
// Bad: verb in URL
@PutMapping("/sandbox-deployments/{id}/stop")

// Option 1: PATCH the status field
@PatchMapping("/sandbox-deployments/{id}")
// body: { "status": "INACTIVE" }

// Option 2: DELETE if stopping = removing the active deployment
@DeleteMapping("/sandbox-deployments/{id}")
```

---
