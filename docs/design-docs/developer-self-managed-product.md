# Developer Self-Managed Product Design

## 1. Goal

Support developers creating their own products and applying to publish them to their portal, with admin approval. No modifications to the `product` or `publication` tables.

## 2. Core Decisions

| Decision | Rationale |
|----------|-----------|
| New table `product_creator` for developer products only | No migration needed; admin products identified by `product.admin_id IS NOT NULL AND no product_creator row` |
| New table `publication_request` for approval flow | Admin products skip this; approved requests write to existing `publication` table |
| `product.admin_id = developerId` for developer products | Reuses `findByNameAndAdminId` for per-owner name dedup; no magic values |
| Keep `uk_name` global unique constraint | Acceptable trade-off for not modifying `product` table; can relax later via Flyway |

## 3. DDL

```sql
-- Only records developer-created products (admin products have no row here)
CREATE TABLE IF NOT EXISTS product_creator (
    product_id  VARCHAR(64)  NOT NULL PRIMARY KEY,
    developer_id VARCHAR(64) NOT NULL,
    created_at  DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Publication request and approval for developer products
CREATE TABLE IF NOT EXISTS publication_request (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    request_id    VARCHAR(64)  NOT NULL,
    product_id    VARCHAR(64)  NOT NULL,
    portal_id     VARCHAR(64)  NOT NULL,
    developer_id  VARCHAR(64)  NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(256) DEFAULT NULL,
    created_at    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    UNIQUE KEY uk_product_portal (product_id, portal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## 4. Business Rules

### 4.1 Product Creation

| Role | `product.admin_id` | `product_creator` row |
|------|--------------------|-----------------------|
| Admin | adminId (unchanged) | None |
| Developer | developerId | `(productId, developerId)` |

Name dedup: existing `findByNameAndAdminId(name, ownerId)` works for both — admin under adminId, developer under developerId.

### 4.2 Publication Flow

```
Admin product:  publishProduct() → INSERT publication → product.status = PUBLISHED

Developer product:
  applyForPublication()  → INSERT publication_request (PENDING)
  approveRequest()       → UPDATE publication_request = APPROVED
                           INSERT publication
                           product.status = PUBLISHED
  rejectRequest(reason)  → UPDATE publication_request = REJECTED, reject_reason
```

### 4.3 Permission Matrix

| Operation | Admin | Developer (owner) | Developer (other) |
|-----------|-------|-------------------|-------------------|
| Create | Yes | Yes | No |
| Update | Yes (all) | Yes (own only) | No |
| Delete | Yes (all) | Yes (own, not published) | No |
| Apply publish | — | Yes (own) | No |
| Approve/Reject | Yes | No | No |
| Unpublish | Yes (all) | No | No |
| View portal listing | Yes | Published only | Published only |
| View own products | — | All statuses | No |

Ownership check: `product_creator` row exists AND `developer_id = currentUser`.

## 5. Key API Changes

### 5.1 Modified (auth only, logic unchanged)

| Endpoint | Auth change |
|----------|-------------|
| `POST /products` | `@AdminAuth` → `@AdminOrDeveloperAuth` |
| `PUT /products/{id}` | `@AdminAuth` → `@AdminOrDeveloperAuth` + ownership check |
| `DELETE /products/{id}` | `@AdminAuth` → `@AdminOrDeveloperAuth` + ownership check |
| `POST /products/{id}/ref` | `@AdminAuth` → `@AdminOrDeveloperAuth` + ownership check |
| `DELETE /products/{id}/ref` | `@AdminAuth` → `@AdminOrDeveloperAuth` + ownership check |

### 5.2 New endpoints

```
GET    /products/my                                    @DeveloperAuth
       → list products created by current developer (all statuses)

POST   /products/{id}/publications/apply               @DeveloperAuth
       → body: { portalId }
       → create publication_request (PENDING)
       → validate: product_creator.developer_id = currentUser, portalId = own portal

GET    /publication-requests                            @AdminAuth
       → query: status, portalId, productId
       → list pending/approved/rejected requests

POST   /publication-requests/{requestId}/approve        @AdminAuth
       → approve + INSERT publication + product.status = PUBLISHED

POST   /publication-requests/{requestId}/reject         @AdminAuth
       → body: { rejectReason }
```

## 6. Key Query Logic

### 6.1 Portal listing — ZERO CHANGE

```java
// Existing ProductServiceImpl.listProducts — no modification needed
if (!contextHolder.isAdministrator()) {
    param.setPortalId(contextHolder.getPortal());
    param.setStatus(ProductStatus.PUBLISHED);
}
// Joins publication table → approved developer products are there too
```

### 6.2 Developer: my products

```sql
SELECT p.* FROM product p
JOIN product_creator pc ON p.product_id = pc.product_id
WHERE pc.developer_id = :developerId
ORDER BY p.updated_at DESC
```

### 6.3 Admin: publication requests

```sql
SELECT pr.* FROM publication_request pr
WHERE pr.status = :status          -- optional filter
  AND pr.portal_id = :portalId    -- optional filter
ORDER BY pr.created_at ASC
```

### 6.4 Ownership check (in service layer)

```java
private void checkOwnership(String productId) {
    if (contextHolder.isAdministrator()) return;
    ProductCreator creator = productCreatorRepository.findByProductId(productId)
        .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "Not your product"));
    if (!creator.getDeveloperId().equals(contextHolder.getUser())) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "Not your product");
    }
}
```

### 6.5 Delete guard for published developer products

```java
if (contextHolder.isDeveloper() && publicationRepository.existsByProductId(productId)) {
    throw new BusinessException(ErrorCode.INVALID_REQUEST,
        "Published product cannot be deleted");
}
```

## 7. Implementation Phases

| Phase | Scope |
|-------|-------|
| 1 | Flyway V20 + `ProductCreator` / `PublicationRequest` entities + repositories |
| 2 | Service layer: `createProduct` branch, ownership checks, `listMyProducts` |
| 3 | Service layer: `applyForPublication`, `approveRequest`, `rejectRequest` |
| 4 | Controller: auth annotation changes + new endpoints |
| 5 | Frontend: developer "My Products" page + apply flow; admin approval page |
