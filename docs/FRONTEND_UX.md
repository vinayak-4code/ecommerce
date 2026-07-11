# Frontend UX Notes

The UI is served by Spring Boot/Thymeleaf and enhanced with a focused JavaScript/CSS layer. It is intentionally not a separate frontend service, but it behaves like a mature e-commerce product rather than an API form demo.

## Entry points

- `/` or `/dashboard` - public marketplace storefront. Products are visible without login.
- `/customer/login` - Customer login.
- `/customer/signup` - Customer signup.
- `/customer/dashboard` - Authenticated customer workspace.
- `/seller/login` - Seller login.
- `/seller/signup` - Seller signup.
- `/seller/dashboard` - Seller Center.
- `/admin/login` - Product Admin login.
- `/admin/dashboard` - Product Admin Console.

## UX principles

- Product discovery is public.
- Login is required only for cart, checkout, orders, account, seller operations, and admin operations.
- UI hiding is not trusted for security; REST APIs enforce role authorization and ownership.
- Use marketplace-style product cards, category sidebars, offer badges, drawers, modals, tabs, and tables.
- Keep workflows role-specific so Product Admin, Seller, and Customer see only what they need.

## Customer storefront

Customer/public UI includes:

- Hero banner and marketplace search.
- Category strip and left category tree.
- Product grid displayed by default without needing a search.
- Product cards with stock status, price, visible attributes, and coupon badges.
- Product detail modal with customer-visible specs and eligible offers.
- Login-required modal when a guest attempts to add to cart.
- Cart drawer with quantity update, remove, live stock validation, coupon picker, and checkout.
- Order history and account page for authenticated customers.

## Seller Center

Seller Center uses a left navigation console:

- Overview
- Profile
- Products
- New Product
- Promotions
- Warehouses
- Inventory
- Orders

The new product form is category-driven:

1. Seller selects a leaf category/sub-category.
2. UI loads that category's predefined attributes.
3. Mandatory and optional attributes are rendered separately.
4. Attribute field types are based on Product Admin metadata such as data type, allowed values, and constraints.

Seller order page shows only seller-owned order lines and allows ship/cancel actions on those lines.

## Product Admin Console

Product Admin Console uses a governance layout:

- Overview metrics.
- Collapsible category hierarchy cards.
- Leaf-only attribute editor.
- Attribute metadata editor for label text, data type, mandatory flag, customer visibility, and constraints.
- Coupon list with derived lifecycle badges.
- Coupon create/edit modal with live/expiry dates and category eligibility.

The old permission tab was intentionally removed because authorization is enforced by Spring Security and service-level ownership checks.
