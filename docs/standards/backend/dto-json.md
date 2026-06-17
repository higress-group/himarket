# Backend DTO and JSON Standards

These detailed standards are part of the HiMarket backend coding standards. See
[../README.md](../README.md) for the standards index and [README.md](README.md) for the backend
overview.

---

## InputConverter / OutputConverter

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
        .toList();

// Optional with map
return productRefRepository
        .findFirstByProductId(productId)
        .map(ref -> new ProductRefResult().convertFrom(ref))
        .orElse(null);
```

---


## JSON and Polymorphic Config

Use `com.alibaba.himarket.utils.JsonUtil` as the unified JSON entry point for application code.
This keeps ObjectMapper configuration, polymorphic handling, null handling, and error wrapping
consistent across modules.

**Rules:**

- Use `JsonUtil.toJson(...)` for serialization.
- Use `JsonUtil.parse(...)`, `JsonUtil.parseArray(...)`, `JsonUtil.convert(...)`,
  `JsonUtil.readTree(...)`, or `JsonUtil.readObjectNode(...)` for deserialization and tree access.
- Do not instantiate `ObjectMapper` directly in business code.
- Do not build JSON with string concatenation. Use typed DTOs, `ObjectNode`, or `ArrayNode`.
- Persisted JSON config should prefer typed DTOs when the schema is known.
- Use `JsonNode` / `ObjectNode` only when the structure is dynamic or vendor-defined.
- Treat blank or malformed JSON as invalid input unless the business contract explicitly defines a
  default.

```java
String configJson = JsonUtil.toJson(config);
McpConfigResult config = JsonUtil.parse(configJson, McpConfigResult.class);

ObjectNode node = JsonUtil.createObjectNode();
node.put("protocol", "streamable-http");
```

### Polymorphic config

Polymorphic DTOs must have a stable type discriminator in JSON, such as `source`, `type`, or
`protocol`. The discriminator must be present before parsing into the polymorphic parent type.

```json
{
  "protocol": "streamable-http",
  "url": "https://example.com/mcp"
}
```

**Rules:**

- Do not parse JSON into a polymorphic parent type unless the required type id field is present.
- If third-party JSON does not match HiMarket's discriminator format, normalize it into a platform
  DTO first, then serialize with `JsonUtil`.
- Keep original vendor JSON only when it is needed for audit/debug or later re-normalization.
- When storing both raw and normalized config, name the fields clearly, for example
  `connectionConfig` for raw vendor config and `mcpConnection` for the normalized platform config.
- Add or update `@Schema` descriptions for persisted polymorphic fields so OpenAPI users know which
  discriminator is required.

---
