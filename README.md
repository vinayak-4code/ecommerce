# E-commerce Enterprise Spring Boot Backend

Enterprise-style e-commerce backend built with Java 21, Spring Boot, Gradle, Spring Security, Spring Data JPA, Flyway, PostgreSQL, HikariCP, Docker, and layered package boundaries.

The project intentionally avoids an API Gateway. It exposes a single Spring Boot backend with simple Bearer-token authentication and role-aware signup/login for `SELLER` and `CUSTOMER`.

## What is implemented

- Seller signup/login and customer signup/login
- Simple Bearer token authorization
- Seller profile and warehouse management
- Category hierarchy and category-specific attribute definitions
- Seller-owned product management
- Product publish/unpublish/delete
- Product version history with latest-50 retention logic
- Product rollback from stored version snapshot
- Warehouse-level inventory management
- Pessimistic-lock based inventory reservation to prevent overselling
- Domain events persisted using an outbox table
- Product search projection table simulating an Elasticsearch/OpenSearch read model
- Cart add/update/remove/view
- Coupon apply/remove
- Percentage and flat discount strategies
- Product-level and cart-level discount scopes
- Proportional cart-level discount allocation across items
- Order Header + Order Line model
- Notification abstraction for Email, SMS, and In-App
- Controller and service tests
- Dockerfile and docker-compose for image build and PostgreSQL runtime

## Technology stack

- Java 21
- Spring Boot 3.5.15
- Gradle 8.14+
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Hibernate
- Bean Validation
- Flyway
- PostgreSQL
- HikariCP
- H2 for tests
- JUnit 5, Mockito, Spring MockMvc
- Docker

Spring Boot's official documentation currently lists 3.5.15 as a stable release line and the Spring Boot Gradle plugin requires Gradle 8.14+ or 9.x. This project uses that stable 3.5.x line for broad ecosystem compatibility.

## Package structure

```text
com.acme.ecommerce
├── auth
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── repository
│   ├── service
│   └── validation
├── cart
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── repository
│   └── service
├── catalog
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── mapper
│   ├── repository
│   ├── service
│   └── validation
├── common
│   ├── config
│   ├── event
│   ├── exception
│   ├── money
│   └── security
├── coupon
│   ├── controller
│   ├── dto
│   ├── entity
│   ├── enums
│   ├── repository
│   ├── service
│   ├── strategy
│   └── validation
├── customer
├── inventory
├── notification
├── order
├── search
└── seller
```

## Run with Docker Compose

```bash
docker compose up --build
```

The API will be available at:

```text
http://localhost:8080
```

PostgreSQL will be available at:

```text
localhost:5432
```

## Build Docker image only

```bash
./scripts/build-with-docker.sh
```

or directly:

```bash
docker build -t ecommerce-enterprise-springboot:latest .
```

## Run locally without Docker

Requires Java 21, Gradle 8.14+, and PostgreSQL.

```bash
gradle clean test bootRun
```

Default DB configuration:

```text
DB_URL=jdbc:postgresql://localhost:5432/ecommerce
DB_USERNAME=ecommerce
DB_PASSWORD=ecommerce
```

## Run tests

```bash
gradle test
```

The Dockerfile also runs tests during image build:

```bash
docker build -t ecommerce-enterprise-springboot:latest .
```

## Database pooling

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

## Auth flow

### Seller signup

```bash
curl -X POST http://localhost:8080/api/v1/auth/sellers/signup \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "seller@example.com",
    "password": "Password1",
    "businessName": "Acme Electronics",
    "contactNumber": "9999999999"
  }'
```

### Customer signup

```bash
curl -X POST http://localhost:8080/api/v1/auth/customers/signup \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "customer@example.com",
    "password": "Password1",
    "fullName": "Customer One",
    "phoneNumber": "8888888888"
  }'
```

### Login

Login requires the expected role, so seller/customer identity is explicit:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "seller@example.com",
    "password": "Password1",
    "role": "SELLER"
  }'
```

Use the returned token:

```bash
Authorization: Bearer <accessToken>
```

## Key APIs

### Seller and warehouse

```text
GET  /api/v1/sellers/me
POST /api/v1/sellers/warehouses
GET  /api/v1/sellers/warehouses
PUT  /api/v1/sellers/warehouses/{warehouseId}
```

### Categories

```text
POST /api/v1/categories
POST /api/v1/categories/{categoryId}/attributes
GET  /api/v1/categories
GET  /api/v1/categories/{categoryId}
```

### Products

```text
POST   /api/v1/products
PUT    /api/v1/products/{productId}
GET    /api/v1/products/{productId}
PATCH  /api/v1/products/{productId}/publish
PATCH  /api/v1/products/{productId}/unpublish
DELETE /api/v1/products/{productId}
GET    /api/v1/products/{productId}/versions
POST   /api/v1/products/{productId}/rollback/{versionNumber}
```

### Inventory

```text
PUT /api/v1/inventory/adjustments
GET /api/v1/inventory/products/{productId}/warehouses/{warehouseId}
```

### Search

```text
GET /api/v1/search/products?q=phone&categoryId=<uuid>&attr_ram=8GB&page=0&size=20&sortBy=price&direction=ASC
```

### Coupons

```text
POST /api/v1/coupons
GET  /api/v1/coupons/{code}
```

### Cart

```text
GET    /api/v1/cart
POST   /api/v1/cart/items
PUT    /api/v1/cart/items/{productId}
DELETE /api/v1/cart/items/{productId}
POST   /api/v1/cart/coupon
DELETE /api/v1/cart/coupon
```

### Orders

```text
POST /api/v1/orders
GET  /api/v1/orders
GET  /api/v1/orders/{orderId}
```

## Design decisions

### 1. Modular monolith, enterprise package boundaries

The code is one deployable Spring Boot application but divided by business capability. Each module has controllers, DTOs, entities, enums, repositories, services, and validators where applicable.

### 2. Simple Bearer token auth

The project avoids OAuth/JWT complexity for the demo. Tokens are random secure values stored in `auth_tokens`. The `TokenAuthenticationFilter` maps `Authorization: Bearer <token>` to a Spring Security principal.

### 3. Role-aware signup/login

Seller and customer signup are separate endpoints. Login requires a `role` field, so a seller cannot accidentally log in through the customer flow.

### 4. Inventory correctness

Inventory reservation uses database pessimistic locks on inventory rows. The service decrements `availableQuantity` and increments `reservedQuantity` in the same transaction, preventing the last unit from being reserved twice.

### 5. Coupon extensibility

Discount calculation uses a strategy factory. Adding a new discount type means implementing a new strategy instead of rewriting cart/order logic.

### 6. Cart-level discount allocation

Cart-level discounts are proportionally distributed across cart items. This prepares the order model for item-level fulfillment, cancellation, refund, and tax calculation.

### 7. Search projection

A database-backed `product_search_documents` projection is included for demo simplicity. The code is isolated behind `SearchIndexSyncService`, so it can be replaced by Spring Data Elasticsearch/OpenSearch later without changing Catalog, Cart, or Order services.

## Suggested evaluation path

1. Read `docs/ARCHITECTURE.md`.
2. Run `docker compose up --build`.
3. Signup seller and customer.
4. Create warehouse.
5. Create/publish product.
6. Adjust inventory.
7. Create coupon.
8. Add item to cart and apply coupon.
9. Place order.
10. Review tests under `src/test/java`.
