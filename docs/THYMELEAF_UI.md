# Thymeleaf Reviewer UI

The application includes a lightweight Thymeleaf UI to demonstrate the e-commerce journey without requiring Postman. The UI intentionally calls the same REST APIs documented in `docs/API.md`; it does not bypass authorization or business rules.

## Base URLs

| Journey | URL | Seeded login | What it demonstrates |
|---|---|---|---|
| Journey selector | `/` or `/dashboard` | None | Links to each role-specific workspace. |
| Product Admin | `/admin` | `admin@example.com` / `Password1` / `PRODUCT_ADMIN` | Category tree, mandatory/optional attributes, coupon creation/listing. |
| Seller | `/seller` | `seller@example.com` / `Password1` / `SELLER` | Warehouse creation, product creation/publish, inventory update, coupon enrollment. |
| Customer | `/customer` | `customer@example.com` / `Password1` / `CUSTOMER` | Product search, cart, coupon apply/remove, order placement, order listing. |

## How authentication works in the UI

1. The page posts to `POST /api/v1/auth/login` with email, password, and role.
2. The returned `accessToken` is stored in browser local storage under a role-specific key.
3. API actions send `Authorization: Bearer <token>`.
4. Logout removes the token from local storage. The REST logout endpoint still exists for server-side revocation.

This keeps the demo aligned with the API requirement of simple Bearer authorization while avoiding stateful server-side UI sessions.

## Why the UI is intentionally small

The assignment is backend-focused. The UI is a reviewer aid for basic operations, not a production storefront. It demonstrates the important flow:

Product Admin category/coupon setup -> Seller product/inventory/enrollment -> Customer search/cart/coupon/order.
