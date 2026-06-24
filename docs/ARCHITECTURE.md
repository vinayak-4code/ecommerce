# Architecture

## Style

This project is an enterprise-style modular monolith. It is intentionally not a microservice system and does not include an API Gateway. The goal is to demonstrate clean boundaries, correct business rules, and an easy path to future service extraction.

## Runtime components

```text
Client
  |
  | HTTP + Authorization: Bearer <token>
  v
Spring Boot Application
  |
  |-- Auth Module
  |-- Seller Module
  |-- Catalog Module
  |-- Inventory Module
  |-- Coupon Module
  |-- Cart Module
  |-- Order Module
  |-- Notification Module
  |-- Search Projection Module
  |
  v
PostgreSQL + HikariCP
```

## Persistence

PostgreSQL is the source of truth. Flyway owns schema creation and seed data. HikariCP is configured as the connection pool.

For local tests, H2 is used with the `test` profile.

## Security

Security uses a simple Bearer token model:

1. Seller or customer signs up.
2. Password is stored using BCrypt.
3. A secure random access token is created.
4. Token is stored in `auth_tokens`.
5. `TokenAuthenticationFilter` reads `Authorization: Bearer <token>`.
6. The token maps to `AuthenticatedUser`.
7. Spring Security enforces role-based endpoint access.

Roles are represented by enum values:

```java
SELLER
CUSTOMER
```

## Package boundaries

Each module follows a consistent package structure:

```text
controller  - HTTP endpoints only
dto         - request and response records
entity      - JPA entities
enums       - controlled value sets
repository  - Spring Data CRUD repositories
service     - business use cases
validation  - domain validation rules
mapper      - entity-to-response mapping where useful
```

## Seller Management

The seller module handles:

- seller profile
- warehouse creation
- warehouse listing
- warehouse updates

Warehouses are intentionally simple. They contain address/configuration fields but no location-aware routing logic.

## Catalog Management

The catalog module handles:

- category hierarchy
- category-specific attributes
- product CRUD
- publish/unpublish
- soft delete
- version history
- rollback

Product attributes are validated against category definitions. Example: a mobile phone category can require `ram` and `storage`, while a laptop category can require `processor`, `ram`, and `storage`.

## Product Versioning

Every meaningful product change creates a product snapshot in `product_versions`.

The version store keeps the latest 50 versions per product.

Rollback flow:

1. Load a previous product snapshot.
2. Validate its attributes against the current category definition.
3. Apply fields to the product.
4. Increment product version.
5. Write a new version snapshot.
6. Publish a product update event.

## Inventory Management

Inventory is maintained at warehouse level using `inventory_items`.

Important fields:

```text
product_id
warehouse_id
available_quantity
reserved_quantity
version
```

Reservation uses pessimistic locking:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

Order placement calls `InventoryService.reserve(...)`. The service locks all available inventory rows for the product, verifies that enough stock exists, decrements available quantity, increments reserved quantity, and creates reservation records in the same transaction.

This ensures that when quantity is 1 and two customers try to order simultaneously, only one transaction can reserve that last unit.

## Coupon and Discount Management

Coupon model supports:

- percentage discounts
- flat discounts
- cart-level discounts
- product-level discounts

Discount calculation uses strategy classes:

```text
DiscountStrategy
├── PercentageDiscountStrategy
└── FlatDiscountStrategy
```

This avoids hardcoding discount logic inside Cart or Order services.

## Cart Management

Cart supports:

- add item
- update quantity
- remove item
- view cart
- apply coupon
- remove coupon

Only one coupon can be applied to a cart at a time.

For cart-level coupons, the total discount is proportionally distributed across items:

```text
item discount = item subtotal / cart subtotal * total cart discount
```

The final item absorbs rounding remainder so the sum of item discounts always equals the cart-level discount.

## Order Management

The order module uses:

- Order Header: `customer_orders`
- Order Lines: `order_lines`

This supports item-level fulfillment and future partial cancellation/refund flows.

Order placement flow:

1. Load customer cart.
2. Price cart and validate coupon.
3. Create order header.
4. Reserve inventory per product.
5. Create order lines per reservation/warehouse allocation.
6. Mark order as placed.
7. Mark cart as ordered and clear active cart items.
8. Publish `ORDER_CREATED` event.

## Events and Outbox

The app persists domain events into `outbox_events`.

Current implementation also publishes Spring application events in-process so the search and notification modules can react immediately during the demo.

Future Kafka integration can be added by creating an outbox publisher that reads pending outbox records and sends them to Kafka topics.

Example event enum values:

```text
PRODUCT_CREATED
PRODUCT_UPDATED
PRODUCT_DELETED
INVENTORY_ADDED
INVENTORY_ADJUSTED
INVENTORY_RESERVED
INVENTORY_RELEASED
ORDER_CREATED
```

## Search Projection

The `product_search_documents` table acts as a local search projection for the demo. It stores denormalized product information:

- product details
- category information
- searchable attributes JSON
- product status
- consolidated available inventory

`SearchIndexSyncService` listens to product and inventory events and updates the projection.

In production, this module would be replaced with OpenSearch/Elasticsearch by changing only the search adapter/repository implementation.

## Notification Framework

Notification is designed behind a channel abstraction:

```java
NotificationChannel.send(NotificationMessage message)
```

Current channels:

- Email
- SMS
- In-App

Future channels:

- Push
- WhatsApp
- third-party messaging platforms

Business modules publish events; notification consumes events. Business services do not know channel-specific implementation details.

## Testing strategy

The test suite includes:

- controller tests with MockMvc
- service tests with Mockito
- validation tests
- coupon pricing math tests
- inventory reservation behavior tests

High-value areas are covered first: authentication shape, cart pricing, discount allocation, attribute validation, and inventory oversell prevention logic.
