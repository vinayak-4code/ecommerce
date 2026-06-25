# E-commerce Enterprise Spring Boot Backend

Enterprise-style e-commerce backend built with Java 21, Spring Boot, Gradle, PostgreSQL, HikariCP, Flyway, Spring Security, Spring Data JPA, Bean Validation, Thymeleaf, and Docker.

The implementation intentionally keeps deployment simple: **no API Gateway** and a single Spring Boot application with clean domain packages. The code is structured like an enterprise modular monolith so the core domains can later be extracted into services if needed.

## Business focus

The project focuses on the areas reviewers are likely to evaluate:

- Product Admin governance for categories, sub-classifications, predefined mandatory/non-mandatory attributes, and coupon configuration.
- Seller ownership of products, warehouses, inventory, and coupon enrollment.
- Customer cart, coupon application, live cart validation, and order placement.
- Carefully handled inventory so the last available unit cannot be purchased twice.
- Denormalized product search projection that represents the Elasticsearch/OpenSearch read model boundary.
- Clear README, architecture notes, write-up, tests, and executable curl examples.

## What is implemented

### Auth and roles

- Simple opaque Bearer-token authorization.
- Seller signup.
- Customer signup.
- Login with explicit role: `PRODUCT_ADMIN`, `SELLER`, or `CUSTOMER`.
- Logout revokes the server-side token.
- Seeded Product Admin user for governance APIs.

### Product Admin capabilities

Product Admin, not Seller/Customer, can:

- Create/update categories.
- Create/update sub-classifications using parent category IDs.
- Configure predefined category attributes; updates add/change attributes without deleting existing product-linked definitions.
- Mark attributes as mandatory or optional.
- Create/update coupons.
- Restrict coupons to category hierarchies.

### Seller capabilities

Seller can:

- Manage seller-owned warehouses.
- Create/update/delete/publish/unpublish seller-owned products.
- Provide only category-defined attributes when creating products.
- Update single warehouse inventory.
- Update inventory in bulk.
- Enroll seller-owned products into Product Admin-created coupons.

### Customer capabilities

Customer can:

- Add/update/remove cart items.
- View cart with live product price and live inventory status.
- Apply/remove one coupon at a time.
- Place orders.
- List orders with pagination.

### Coupon model

Supported discount types:

- `FLAT`: fixed amount discount, capped by eligible subtotal.
- `UPTO_PERCENT_OFF`: percentage discount capped by a required positive `maxDiscountAmount`.

Supported scope values:

- `CART`
- `CATEGORY`
- `PRODUCT`

Important rules:

- Only one coupon can be applied to a cart at a time.
- The same product may be enrolled into multiple coupons.
- A coupon only discounts enrolled products that also satisfy category eligibility.
- Category eligibility is hierarchy-aware: a coupon on `Electronics` can apply to `Mobile Phones` and `Laptops` products if the seller enrolled those products.
- Cart-level discounts are allocated proportionally across eligible cart lines for clean order/refund/tax support.
- Coupons have live and expiry dates.
- `CATEGORY` scoped coupons must define at least one eligible category.

### Inventory model

- Inventory is stored per product and warehouse.
- Quantity `0` is valid and means out of stock.
- Single and bulk inventory update APIs are available.
- Purchase reservation uses conditional PostgreSQL updates: `available_quantity >= requested_quantity`.
- Reservation then consumption happens during order placement.
- This avoids overselling without explicit application-level pessimistic locks on seller stock updates.

### Search model

Product management remains in PostgreSQL as the source of truth. Search uses a denormalized projection table named `product_search_documents`, which simulates the document shape that would be stored in Elasticsearch/OpenSearch.

The projection contains:

- Product details
- Category information
- Searchable attributes JSON
- Product status
- Consolidated inventory across warehouses

For the demo, using PostgreSQL for this projection keeps the project runnable with only Docker Compose. The architecture leaves a clear `search` package boundary so the repository can be replaced by Elasticsearch/OpenSearch later.

### Removed from current scope

The notification service was removed from the runnable implementation based on the latest scope. It is documented as a future enhancement in `docs/ARCHITECTURE.md`.

## Technology stack

- Java 21
- Spring Boot 3.5.15
- Gradle 8.14+
- Spring Web MVC
- Spring Security
- Spring Data JPA / Hibernate
- Bean Validation
- Flyway
- PostgreSQL
- HikariCP
- H2 for tests
- Thymeleaf role-based demo UI for Product Admin, Seller, and Customer journeys
- JUnit 5, Mockito, Spring MockMvc
- Docker and Docker Compose

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
├── order
├── search
├── seller
└── ui
```

## Run with Docker Compose

```bash
docker compose up --build
```

API:

```text
http://localhost:8080
```

Simple reviewer dashboard and role-specific Thymeleaf journeys:

```text
http://localhost:8080/dashboard
http://localhost:8080/admin
http://localhost:8080/seller
http://localhost:8080/customer
```

PostgreSQL:

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

HikariCP is configured in `src/main/resources/application.yml`:

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

## Thymeleaf UI journeys

The project includes a small UI so reviewers can exercise the backend without Postman:

| Role | URL | Main operations |
|---|---|---|
| Product Admin | `/admin` | Login, create category/sub-classification, add predefined mandatory/optional attributes, create/list coupons. |
| Seller | `/seller` | Login, view seller profile, create warehouse, create product, publish product, set inventory, enroll product into coupon. |
| Customer | `/customer` | Login, search products, add to cart, apply/remove coupon, validate cart totals/stock, place order, list orders. |

The UI stores the Bearer token in browser local storage and calls the same REST APIs used by `docs/curl-requests.sh`. It is intentionally simple because the assignment is backend-focused. See `docs/THYMELEAF_UI.md`.

## Documentation map

- `docs/API.md`: API examples and representative responses.
- `docs/curl-requests.sh`: executable curl journey over seeded data.
- `docs/CLASS_GUIDE.md`: class-by-class responsibility guide.
- `docs/SERVICE_CONTROLLER_GUIDE.md`: controller/service documentation with examples.
- `docs/THYMELEAF_UI.md`: role-specific UI journey guide.
- `docs/ARCHITECTURE.md`: architecture, trade-offs, and follow-up improvements.

## Seeded demo users

All seeded users use password:

```text
Password1
```

| Role | Email |
|---|---|
| PRODUCT_ADMIN | `admin@example.com` |
| SELLER | `seller@example.com` |
| CUSTOMER | `customer@example.com` |

Seeded product IDs:

| Product | ID | Category |
|---|---|---|
| Pixel Demo Phone | `00000000-0000-0000-0000-000000003001` | Mobile Phones |
| ThinkBook Demo Laptop | `00000000-0000-0000-0000-000000003002` | Laptops |
| Cotton Demo Shirt | `00000000-0000-0000-0000-000000003003` | Apparel / Men |

Seeded coupons:

| Coupon | Type | Scope | Notes |
|---|---|---|---|
| `ELECTRO10` | `UPTO_PERCENT_OFF` | `CATEGORY` | Electronics only, capped at 500 |
| `FLAT200` | `FLAT` | `CART` | Flat 200 on enrolled products |
| `APPAREL50` | `FLAT` | `CATEGORY` | Apparel only |

## Curl test script

Detailed class notes are in `docs/CLASS_GUIDE.md`, API samples are in `docs/API.md`, and a full executable curl script is included:

```bash
./docs/curl-requests.sh
```

It demonstrates:

- Login for Product Admin, Seller, and Customer
- Paginated category listing
- Product search projection
- Product Admin category creation
- Product Admin coupon creation
- Seller coupon product enrollment
- Single and bulk inventory update
- Cart add/view/apply coupon
- Order placement with inventory reservation and consumption
- Paginated order listing
- Logout

## Key API examples

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "customer@example.com",
    "password": "Password1",
    "role": "CUSTOMER"
  }'
```

### Product search

```bash
curl 'http://localhost:8080/api/v1/search/products?q=demo&page=0&size=10&attr_ram=8GB'
```

### Add item to cart

```bash
curl -X POST http://localhost:8080/api/v1/cart/items \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": "00000000-0000-0000-0000-000000003001",
    "quantity": 1
  }'
```

### Apply coupon

```bash
curl -X POST http://localhost:8080/api/v1/cart/coupons \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"code":"ELECTRO10"}'
```

### Place order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"shippingAddress":"Demo Address, Bengaluru"}'
```

## Important trade-offs

- Real Elasticsearch/OpenSearch is not included to keep the demo one-command runnable. The search package and projection table model the ES document boundary.
- Real Kafka is not included. Domain events are persisted in an outbox table and also published in-process for the search projection.
- Payment is represented by order/payment statuses, not a real payment gateway.
- Warehouse location-aware fulfillment is intentionally deferred.
- Notifications are deferred as a future enhancement.
