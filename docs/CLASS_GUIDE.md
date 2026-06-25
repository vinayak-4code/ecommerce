# Class Guide for Reviewers

This guide explains the important classes and the kind of example each class supports. It is intentionally concise so a panel can scan the project quickly before reading the code.

## Auth

| Class | Purpose | Example |
|---|---|---|
| `AuthController` | Exposes seller/customer signup, role-aware login, and logout. | `POST /api/v1/auth/login` with `role=CUSTOMER`. |
| `AuthService` | Creates users/profiles and issues tokens. | Seller signup creates `UserAccount(role=SELLER)` plus `SellerProfile`. |
| `TokenAuthenticationService` | Issues, validates, and revokes opaque Bearer tokens. | Logout revokes the stored `AuthToken`. |
| `TokenAuthenticationFilter` | Reads `Authorization: Bearer ...` and populates Spring Security context. | Cart endpoints resolve `CurrentUser.require()`. |
| `UserAccount` | Login identity for Product Admin, Seller, or Customer. | Seeded `admin@example.com` has role `PRODUCT_ADMIN`. |
| `AuthToken` | Server-side token storage. | Revoked token cannot authenticate further calls. |
| `UserRole` | Enum for role-based authorization. | `PRODUCT_ADMIN`, `SELLER`, `CUSTOMER`. |

## Catalog

| Class | Purpose | Example |
|---|---|---|
| `CategoryController` | Product Admin category and classification API, public category reads. | Create `Smart Watches` under `Electronics`. |
| `CategoryService` | Maintains category hierarchy and predefined attributes. | Defines mandatory `ram` and optional `battery_capacity`. |
| `ProductController` | Seller product management API. | Seller creates a mobile phone product with category-specific attributes. |
| `ProductService` | Owns product CRUD, publish/unpublish, delete, and rollback. | Publishing a product emits `PRODUCT_PUBLISHED`. |
| `ProductVersionService` | Captures latest 50 product snapshots and restores old versions. | Roll back product price/attributes to version 2. |
| `ProductAttributeValidator` | Enforces required and allowed attributes per category. | Reject product missing required `storage`. |
| `Category` | Hierarchical classification entity. | `Electronics -> Mobile Phones`. |
| `CategoryAttributeDefinition` | Predefined category attribute metadata. | `ram`, `STRING`, required, searchable. |
| `Product` | Seller-owned product source of truth. | Price and status are loaded from this entity during cart view. |
| `ProductAttributeValue` | Product-specific values for category-defined attributes. | `ram=8GB`. |
| `ProductVersion` | Product snapshot history. | Stores JSON snapshot for rollback. |

## Coupon

| Class | Purpose | Example |
|---|---|---|
| `CouponController` | Product Admin coupon CRUD and Seller product enrollment endpoints. | Seller enrolls phone in `ELECTRO10`. |
| `CouponService` | Validates definitions, category eligibility, and seller enrollment. | Product can be in `ELECTRO10` and `FLAT200`. |
| `CouponValidator` | Validates live date, expiry date, status, min cart, and discount value. | Reject expired coupon. |
| `DiscountStrategyFactory` | Selects discount calculator by enum. | `UPTO_PERCENT_OFF` uses capped percentage strategy. |
| `FlatDiscountStrategy` | Calculates fixed discount capped by eligible subtotal. | `FLAT200` subtracts up to 200. |
| `UpToPercentOffDiscountStrategy` | Calculates percentage discount capped by max amount. | 10% capped at 500. |
| `Coupon` | Product Admin coupon definition. | `ELECTRO10`, category scope, live/expiry dates. |
| `CouponCategoryEligibility` | Optional category restriction. | Electronics coupon does not apply to Apparel. |
| `CouponProductEnrollment` | Seller opt-in for a product and coupon. | Seller opts phone into `ELECTRO10`. |

## Seller and Warehouse

| Class | Purpose | Example |
|---|---|---|
| `SellerController` | Reads seller profile. | Get current seller profile. |
| `WarehouseController` | Seller warehouse CRUD/list API. | Create a fulfillment hub. |
| `SellerService` | Loads seller profile by authenticated user. | Used by product and inventory services. |
| `WarehouseService` | Ensures warehouse belongs to the authenticated seller. | Reject inventory update for another seller warehouse. |
| `SellerProfile` | Seller business profile. | `Acme Demo Seller`. |
| `Warehouse` | Seller fulfillment location. | `BLR-01`. |

## Inventory

| Class | Purpose | Example |
|---|---|---|
| `InventoryController` | Seller single/bulk inventory update and inventory lookup. | Set available quantity to `0` to show out of stock. |
| `InventoryService` | Manages warehouse stock, reservation, release, and consumption. | Conditional update prevents last unit oversell. |
| `InventoryItemRepository` | Contains conditional reservation update query. | `availableQuantity >= requested`. |
| `InventoryItem` | Stock per product and warehouse. | `product=phone`, `warehouse=BLR-01`, `available=5`. |
| `InventoryReservation` | Reservation created during order checkout. | Reserved then consumed when order is placed. |
| `InventoryReservationAllocation` | Service DTO for how quantity was allocated across warehouses. | One order line may map to one warehouse allocation. |

## Cart

| Class | Purpose | Example |
|---|---|---|
| `CartController` | Customer cart endpoints. | Add item, apply coupon, view cart. |
| `CartService` | Owns cart commands and one-coupon-at-a-time rule. | Quantity `0` removes the item. |
| `CartPricingService` | Reprices cart using live product price, inventory, and coupon rules. | Cart view shows `checkoutReady=false` if stock is insufficient. |
| `CartPricingResult` | Internal calculated cart response model. | Carries line discount allocation. |
| `Cart` | Active customer cart header. | Stores customer and coupon code. |
| `CartItem` | Cart line with product ID and quantity. | Product price is not stored here to avoid stale totals. |
| `CartItemStockStatus` | Enum for live stock status. | `IN_STOCK`, `OUT_OF_STOCK`, `INSUFFICIENT_STOCK`. |

## Order

| Class | Purpose | Example |
|---|---|---|
| `OrderController` | Customer order placement, get, and paginated list. | `POST /api/v1/orders`. |
| `OrderService` | Converts priced cart into order header and order lines. | Reserves inventory, creates lines, consumes reservation. |
| `CustomerOrder` | Order header. | Customer, status, totals, shipping address. |
| `OrderLine` | Item-level order line. | Product ID, quantity, price, discount, fulfillment status. |
| `OrderStatus` | Order lifecycle enum. | `CREATED`, `PLACED`, `CANCELLED`. |
| `FulfillmentStatus` | Line-level fulfillment enum. | Enables future partial fulfillment. |

## Search

| Class | Purpose | Example |
|---|---|---|
| `ProductSearchController` | Public product search/listing endpoint. | `GET /api/v1/search/products?attr_ram=8GB`. |
| `ProductSearchService` | Reads denormalized search projection and maps results. | Supports query, category, attribute filters, pagination, sorting. |
| `SearchIndexSyncService` | Updates search projection from product/inventory events. | Inventory update refreshes consolidated availability. |
| `ProductSearchDocument` | Search read model document stored in Postgres for demo. | Equivalent to future Elasticsearch document. |

## Common infrastructure

| Class | Purpose | Example |
|---|---|---|
| `SecurityConfig` | Role-based endpoint authorization. | Product Admin can create coupons; Seller cannot. |
| `DataSourcePoolConfig` | HikariCP-backed data source. | Pool size from environment variables. |
| `GlobalExceptionHandler` | Converts exceptions to consistent API errors. | Business exception returns JSON error body. |
| `MoneyUtil` | Central money rounding and percentage math. | Discount allocation uses scale 2. |
| `DomainEventPublisher` | Saves events to outbox and publishes in-process events. | `PRODUCT_UPDATED` updates search projection. |
| `OutboxEvent` | Persisted event record for future Kafka publisher. | Production publisher can read `outbox_events`. |
| `DashboardController` | Simple Thymeleaf reviewer dashboard. | Shows seeded users and curl script pointer. |
