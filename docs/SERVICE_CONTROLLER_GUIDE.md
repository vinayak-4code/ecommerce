# Service and Controller Documentation

This document complements the JavaDoc comments in the code. It explains what each controller and service owns and gives a concrete example for reviewers.

## Auth

### `AuthController`
REST entry point for role-specific signup, role-aware login, and logout.

Example: `POST /api/v1/auth/login` with `role=SELLER` only authenticates a seller account; the same email/password cannot be used with a different role.

### `AuthService`
Creates `UserAccount`, `SellerProfile`, and `CustomerProfile` records and delegates token creation. Product Admin accounts are seeded to keep governance controlled.

Example: seller signup creates `UserAccount(role=SELLER)` and `SellerProfile` in one transaction.

### `TokenAuthenticationService`
Issues, validates, and revokes server-side opaque Bearer tokens.

Example: logout marks the token revoked so the filter cannot authenticate it again.

## Catalog

### `CategoryController`
Product Admin category API and public category read API.

Example: create `Electronics -> Mobile Phones` with mandatory `ram` and `storage`, optional `battery_capacity`.

### `CategoryService`
Maintains category hierarchy and predefined attributes. It intentionally does not allow arbitrary seller-defined attributes because product search filters should be predictable.

Example: if Product Admin marks `storage` required for Mobile Phones, seller product creation must include it.

### `ProductController`
Seller product management API.

Example: seller creates a draft product, publishes it, views versions, and rolls back if needed.

### `ProductService`
Owns product creation, update, publish/unpublish, soft delete, ownership validation, attribute validation, version capture, and event publication.

Example: product update captures the previous state before replacing attributes.

### `ProductVersionService`
Stores product snapshots and retains the latest 50 versions per product.

Example: rollback restores product name, price, category, and attribute values from a selected snapshot.

## Coupon

### `CouponController`
Product Admin creates/updates coupons; sellers enroll/unenroll their own products.

Example: Product Admin creates `ELECTRO10`; seller enrolls `Pixel Demo Phone` into it.

### `CouponService`
Validates coupon setup, dates, category eligibility, and seller ownership during enrollment.

Example: an Electronics coupon cannot be applied to Apparel unless Product Admin configured Apparel eligibility.

## Seller and Warehouse

### `SellerController`
Returns the current seller profile.

Example: `GET /api/v1/sellers/me` shows the seller id needed for debugging ownership.

### `WarehouseController`
Creates, lists, and updates seller warehouses.

Example: seller creates a fulfillment hub that inventory records reference.

### `SellerService`
Resolves seller profile from the authenticated user id.

Example: Product and inventory services call this before changing seller-owned data.

### `WarehouseService`
Validates warehouse ownership and manages simple warehouse fields.

Example: inventory update rejects a warehouse that does not belong to the current seller.

## Inventory

### `InventoryController`
Exposes single inventory update, bulk inventory update, adjustment, and lookup endpoints.

Example: set quantity to `0` for a product/warehouse to show out-of-stock in search and cart.

### `InventoryService`
Maintains warehouse-level stock and handles reservation, consumption, and release.

Example: order placement reserves inventory with a conditional update using `availableQuantity >= requestedQuantity`, preventing two customers from buying the last unit.

## Cart

### `CartController`
Customer cart API.

Example: add product, apply `ELECTRO10`, view cart, and see item-level discount allocation.

### `CartService`
Owns cart commands and the one-coupon-per-cart rule.

Example: applying a second coupon raises a business error until the first coupon is removed.

### `CartPricingService`
Rebuilds cart totals using live product price, current inventory, and coupon rules.

Example: if a seller changes inventory to zero, cart view returns `OUT_OF_STOCK` and `checkoutReady=false`.

## Order

### `OrderController`
Customer order API with placement, get, and paginated list endpoints.

Example: `POST /api/v1/orders` creates an order header and item-level order lines.

### `OrderService`
Orchestrates cart pricing, inventory reservation, order creation, reservation consumption, and cart clearing.

Example: order lines store product, quantity, unit price, discount, tax, total, warehouse, and fulfillment status.

## Search

### `ProductSearchController`
Public product listing/search endpoint.

Example: `GET /api/v1/search/products?q=demo&attr_ram=8GB&page=0&size=10`.

### `ProductSearchService`
Reads the denormalized search projection with filters, pagination, and sorting.

Example: today it reads `product_search_documents`; a later OpenSearch adapter can replace only this boundary.

### `SearchIndexSyncService`
Updates the search projection from domain events.

Example: product publish or inventory update refreshes product status and consolidated availability.

## UI

### `DashboardController`
Renders the role journey selector.

### `AdminUiController`
Renders `/admin` for category and coupon setup.

### `SellerUiController`
Renders `/seller` for seller product, warehouse, inventory, and coupon enrollment operations.

### `CustomerUiController`
Renders `/customer` for product search, cart, coupon, and order operations.
