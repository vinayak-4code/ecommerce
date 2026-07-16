# Architecture

## Architectural style

This project is a **modular monolith**. It intentionally avoids an API Gateway and avoids deploying many services for a take-home/demo project. The code is still split by domain packages so that each module has clear ownership and can be extracted later.

```text
Client / curl / Thymeleaf role UIs (/admin, /seller, /customer)
        |
        v
Spring Boot API
        |
        +-- auth
        +-- catalog
        +-- coupon
        +-- seller
        +-- inventory
        +-- cart
        +-- order
        +-- search projection
        |
        v
PostgreSQL + Flyway + HikariCP
```


## Thymeleaf reviewer UI

The UI layer is deliberately thin and has three dedicated base URLs:

- `/admin` renders Product Admin operations.
- `/seller` renders Seller operations.
- `/customer` renders the Customer shopping journey.

Each page calls the same REST APIs with a Bearer token stored in browser local storage. The UI does not introduce server-side sessions or bypass role authorization, so the API remains the single source of business behavior.

## Role boundaries

### PRODUCT_ADMIN

Owns product-platform governance:

- Category creation/update
- Sub-classification creation/update through `parentId`
- Predefined category attributes
- Mandatory/non-mandatory attribute configuration
- Coupon creation/update
- Coupon category eligibility

Product Admin cannot create seller products, manage seller inventory, or place customer orders.

### SELLER

Owns seller business data:

- Seller profile
- Warehouses
- Products
- Product publish/unpublish
- Inventory updates
- Coupon product enrollment

Seller cannot manage categories or coupon definitions.

### CUSTOMER

Owns shopping flows:

- Cart add/update/remove/view
- Coupon apply/remove
- Order placement
- Order listing

Customer cannot update catalog governance, seller products, or inventory.

## Main modules

### `auth`

Provides signup/login/logout and server-side Bearer token management.

Key classes:

- `AuthController`
- `AuthService`
- `TokenAuthenticationService`
- `UserAccount`
- `AuthToken`
- `UserRole`

Design decision: opaque server-side tokens were chosen over JWT because logout/revocation is straightforward in a demo without token blacklist infrastructure.

### `catalog`

Owns categories, attributes, products, and product version history.

Key classes:

- `CategoryController`
- `CategoryService`
- `ProductController`
- `ProductService`
- `ProductVersionService`
- `ProductAttributeValidator`

Category attributes are predefined by Product Admin. Updates are handled as upserts so existing product-linked definitions are not deleted accidentally. Sellers can only submit attributes that exist for the chosen category. Required attributes are enforced before product creation/update.

Product changes publish domain events so the search projection can be updated.

### `coupon`

Owns Product Admin coupon definitions and Seller product enrollment.

Key classes:

- `CouponController`
- `CouponService`
- `CouponValidator`
- `DiscountStrategyFactory`
- `FlatDiscountStrategy`
- `UpToPercentOffDiscountStrategy`
- `CouponProductEnrollment`
- `CouponCategoryEligibility`

Supported discount types:

- `FLAT`
- `UPTO_PERCENT_OFF` with required max cap

Important behavior:

- A product can be enrolled in multiple coupons.
- A cart can apply only one coupon at a time.
- Discount is applied only to enrolled eligible products.
- Category restrictions are hierarchy-aware, and category-scoped coupons must explicitly define eligible categories.
- Cart-level discounts are distributed proportionally across eligible cart lines.

### `seller`

Owns seller profile and warehouse management.

Warehouse configuration is deliberately simple: address and status only. Location-aware fulfillment can be added later.

### `inventory`

Owns warehouse-level inventory and purchase reservations.

Key classes:

- `InventoryController`
- `InventoryService`
- `InventoryItem`
- `InventoryReservation`
- `InventoryItemRepository`

Inventory rules:

- Quantity `0` is valid.
- Quantity cannot be negative.
- Single and bulk absolute updates are available.
- Purchase reservation uses conditional database updates.

The critical oversell prevention query is conceptually:

```sql
update inventory_items
set available_quantity = available_quantity - :quantity,
    reserved_quantity = reserved_quantity + :quantity
where product_id = :productId
  and warehouse_id = :warehouseId
  and available_quantity >= :quantity;
```

Only one concurrent transaction can successfully decrement the last available unit because the database update is atomic and row-level locking happens inside PostgreSQL.

### `cart`

Owns active customer carts.

Key classes:

- `CartController`
- `CartService`
- `CartPricingService`
- `CartPricingResult`

Cart records store only product IDs, quantities, and coupon code. Cart view reloads:

- Current product status
- Current product price
- Current consolidated inventory
- Current coupon rule
- Current seller enrollment/category eligibility

This keeps cart totals fresh even if product price or inventory changed after the item was added.

### `order`

Owns order placement using Order Header + Order Line model.

Key classes:

- `OrderController`
- `OrderService`
- `CustomerOrder`
- `OrderLine`

Order flow:

1. Load active cart.
2. Reprice cart from live product/inventory/coupon data.
3. Require cart checkout readiness.
4. Create order header.
5. Reserve inventory per product/warehouse.
6. Create order lines.
7. Consume reserved inventory.
8. Mark order placed.
9. Clear active cart.
10. Publish order event.

Order lines store product price, discount, tax, total, and fulfillment status. This supports future item-level cancellation, return, and refund.

### `search`

Owns the product listing/search read model.

The implementation uses `product_search_documents` in PostgreSQL for demo simplicity, but the document shape mirrors the Elasticsearch/OpenSearch projection described in the requirement:

- Product fields
- Category fields
- Attributes JSON
- Status
- Consolidated inventory

`SearchIndexSyncService` listens to product and inventory domain events and updates the projection.

Future replacement path:

```text
Product/Inventory event -> Kafka -> Elasticsearch Sync Service -> Elasticsearch/OpenSearch index
```

The controller/service API does not need to change when the repository implementation changes.

### `common`

Shared infrastructure:

- Security config
- Current user helper
- Exception handling
- Money utilities
- Domain event envelope
- Hikari data source configuration

## Data consistency

### Source of truth

PostgreSQL remains the source of truth for:

- Products
- Categories
- Coupons
- Inventory
- Cart
- Orders

### Search consistency

Search is eventually consistent. Product and inventory updates publish events; the projection updates asynchronously inside the demo process. In a production version, the same events would be written to Kafka and consumed by an Elasticsearch/OpenSearch sync service.

### Events and outbox

Business services publish domain events through `DomainEventPublisher`. Events are saved to `outbox_events`, then also published in-process so projections update during the demo.

This keeps the code close to an outbox/Kafka design without requiring Kafka for local execution.

## Database pooling

HikariCP is configured through `spring.datasource.hikari`.

Default Docker settings:

```yaml
maximum-pool-size: 20
minimum-idle: 5
connection-timeout: 30000
idle-timeout: 600000
max-lifetime: 1800000
```

## Pagination

Paginated APIs include:

- `GET /api/v1/categories`
- `GET /api/v1/coupons`
- `GET /api/v1/search/products`
- `GET /api/v1/orders`
- `GET /api/v1/sellers/warehouses`

## Future enhancements

- Replace search projection repository with real Elasticsearch/OpenSearch.
- Replace in-process event dispatch with Kafka consumers.
- Implement payment gateway integration.
- Add tax calculation service.
- Add item-level cancellation/return/refund APIs.
- Add warehouse location-aware inventory and fulfillment selection.
- Add notification service for email/SMS/in-app messages.
- Add idempotency keys for order placement.
- Add admin audit trails and approval workflows for sensitive catalog changes.
