# VinCommerce Enterprise E-commerce Demo

A Spring Boot + Gradle e-commerce application focused on the original take-home requirements: public product catalogue, authenticated per-user cart, coupon pricing, order placement, and role-based access control. The implementation is a modular monolith: simple to run for review, but organized like an enterprise service.

## What this project demonstrates

- Public product catalogue: guests can browse categories, product listing, product details, and visible product offers without login.
- Customer authentication: customer signup/login uses JWT Bearer authorization.
- Per-user cart isolation: cart and order APIs derive the customer from the JWT, not from request IDs.
- Coupon calculation: one coupon can be applied to a cart, and cart-level discount is allocated proportionally across eligible lines.
- Seller ownership: sellers manage their own warehouses, products, inventory, promotions, and order lines only.
- Product Admin governance: admin manages category hierarchy, leaf-category attributes, and coupon definitions.
- Inventory safety: quantity `0` is valid and shown as out of stock; checkout reserves/consumes inventory using conditional PostgreSQL updates.

## Role-based UI

Open the application at:

```text
http://localhost:8080/
```

The UI is designed as three role-specific workspaces:

| Role | URL | Purpose |
|---|---|---|
| Public / Customer Storefront | `/` or `/customer/dashboard` | Browse products, view coupons, manage cart, checkout, view orders. |
| Seller Center | `/seller` or `/seller/dashboard` | Manage seller products, warehouses, inventory, coupon enrollment, and seller-owned order lines. |
| Product Admin Console | `/admin` or `/admin/dashboard` | Manage categories, leaf attributes, and coupon definitions. |

Guests can browse products. Login is requested only when a user tries to add to cart, place an order, view orders, or enter seller/admin workspaces.

## Seeded users

All seeded users use password:

```text
Password1
```

| Role | Email |
|---|---|
| PRODUCT_ADMIN | `admin@example.com` |
| SELLER | `seller@example.com` |
| CUSTOMER | `customer@example.com` |

## Run with Docker Compose

```bash
docker compose up --build
```

Then open:

```text
http://localhost:8080/
```

Docker Compose starts:

- PostgreSQL
- Spring Boot application

The Dockerfile currently builds the application with tests skipped:

```text
gradle clean bootJar --no-daemon -x test
```

Tests are intentionally not part of this source-completion iteration. They should be added in the next phase after the UI and source behavior are finalized.

## Run locally without Docker

Requires Java 21, Gradle 8.14+, and PostgreSQL.

```bash
gradle clean bootJar -x test
gradle bootRun
```

Default database settings are in `src/main/resources/application.yml` and can be overridden with environment variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/ecommerce
DB_USERNAME=ecommerce
DB_PASSWORD=ecommerce
JWT_SECRET=change-this-secret
TOKEN_EXPIRY_HOURS=12
```

## Database connection pooling

HikariCP is configured in `application.yml`:

```yaml
spring:
  datasource:
    hikari:
      pool-name: ecommerce-hikari-pool
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

## Main feature areas

### Product Admin

- No admin signup; admin is seeded.
- Create/update root categories and sub-categories.
- Attributes are attached only to leaf categories.
- Attribute metadata includes label text, data type, required flag, customer visibility, constraints, and allowed values.
- Create/update coupons in the admin console.
- Coupon dates determine scheduled/live/expired behavior.

### Seller

- Seller signup/login.
- Manage seller profile, warehouses, products, promotions, inventory, and order lines.
- Product form is category-driven: seller selects a leaf category, then the UI renders mandatory and optional attributes from Product Admin metadata.
- Seller can enroll owned products into eligible coupons.
- Seller can view customer order lines only for products they own.
- Seller can ship or cancel their own order lines.

### Customer

- Customer signup/login.
- Browse catalogue without login.
- View category classification and product cards by default.
- See product-level eligible coupons before adding to cart.
- Add/update/remove cart items after login.
- Quantity `0` removes the cart line.
- Removing the last item leaves an empty cart with zero totals.
- Apply one coupon at a time.
- Place order and view own order history.

## Search design

PostgreSQL product tables remain the source of truth. The `product_search_documents` table acts as a denormalized search projection similar to an Elasticsearch/OpenSearch document.

This keeps the demo easy to run while preserving a clean future path:

```text
Product/Inventory writes -> domain event/outbox -> SearchIndexSyncService -> search projection
```

The `search` package can later be replaced by an OpenSearch client without changing cart/order/catalog business flows.

## Auth and authorization

The backend uses signed JWT Bearer tokens:

```text
Authorization: Bearer <jwt>
```

Important authorization rules:

- Public users can browse products and coupons.
- Only customers can access cart and order APIs.
- Only sellers can manage seller products, warehouses, inventory, promotions, and seller order lines.
- Only Product Admin can manage categories and coupons.
- Cart/order ownership is derived from the JWT, not from customer IDs in the request.

## Project structure

```text
src/main/java/com/acme/ecommerce
├── auth
├── cart
├── catalog
├── common
├── coupon
├── customer
├── inventory
├── order
├── search
├── seller
└── ui
```

Each domain has dedicated controller, service, repository, DTO, entity, enum, mapper, and validation classes where applicable.

## Documentation

- `docs/ARCHITECTURE.md` - architecture and trade-offs.
- `docs/FRONTEND_UX.md` - role-based UI/UX design notes.
- `docs/CLASS_GUIDE.md` - reviewer guide for major classes.
- `docs/SERVICE_CONTROLLER_GUIDE.md` - controller/service responsibility guide.
- `WRITEUP.md` - placeholder for the required final take-home write-up; complete this after implementation is frozen.

## Deferred items

- Automated tests will be added after final source behavior is locked.
- Real Elasticsearch/OpenSearch is deferred; the search projection table models the same boundary.
- Kafka is represented by local domain events/outbox for demo simplicity.
- Payment gateway and notifications are documented as future improvements.
