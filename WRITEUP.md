# Engineering Take-Home Write-up

**Author:** Vinayak Mittal

---

## What I built and why

When I first read the assignment, the ask was straightforward — product catalogue, cart, coupons, orders. But I didn't want to build just another CRUD app. I've seen how real e-commerce systems work, and I wanted this to reflect that thinking, even within a take-home scope.

So I built a multi-role e-commerce platform with three distinct workspaces:

- **Product Admin** — owns the catalogue structure (categories, attributes) and defines coupons. Think of this as the marketplace governance layer.
- **Seller** — creates products under admin-defined categories, manages warehouses and inventory, enrolls products into coupons, and fulfills orders. The seller never touches catalogue governance.
- **Customer** — browses publicly, logs in only when needed, manages cart, applies a coupon, and places orders.

I went with a **modular monolith** over microservices. For a take-home, the reviewer should be able to run `docker compose up --build` and have everything working in under a minute. No Kafka, no Elasticsearch cluster, no API gateway. But the code is structured so each domain (auth, catalog, cart, coupon, order, inventory, seller, search) lives in its own package with clear boundaries. If this needed to scale, you'd extract packages into services — the seams are already there.

## How I used AI

I'll be honest — I used AI heavily, but as a tool, not a crutch. Here's the split:

**What AI helped with:**
- Generating the repetitive Spring Boot boilerplate — entities, DTOs, repositories, controller scaffolding. Writing 10 nearly identical CRUD endpoints by hand doesn't prove anything.
- Iterating on the Thymeleaf + vanilla JS frontend. I'm primarily a backend engineer, so I leaned on AI for the UI polish — the cart drawer, modal patterns, category tree rendering, responsive layout tweaks.
- Drafting Flyway migration SQL and seed data. I described the schema I wanted; AI generated the DDL.
- Debugging edge cases — coupon math rounding, inventory race conditions, JWT expiration handling.

**What I decided myself:**
- The entire domain model and role boundaries. Who can do what, and why. This is the architectural backbone.
- Cart ownership derived from JWT, never from request body. This is a security decision I feel strongly about — if a cart API accepts a `customerId` parameter, you're one bug away from cross-user data leaks.
- Attributes only on leaf categories. Early on, the design had attributes on parent categories like "Electronics." That doesn't work — you don't define "RAM" on Electronics, you define it on Mobile Phones. I caught this and restructured it.
- Making product browsing fully public. The first version required login to see products. That's not how any real e-commerce site works. I changed this so login is only prompted when you try to add to cart or checkout.
- Inventory safety through conditional SQL (`WHERE available_qty >= :requested`) instead of application-level locks. Keeps the oversell protection at the transactional boundary where it belongs.

## Where I pushed back on AI output

AI tends to generate "happy path" code. A few things I had to correct or throw away:

1. **Flat package structure** — The initial generated code was too flat. Everything in one package, service methods doing controller-level work. I restructured into proper domain packages with separation between controllers, services, repositories, DTOs, entities, and validation.

2. **Role overlap** — AI initially generated seller endpoints that could create categories and coupons. That's wrong for a marketplace model. Product Admin governs the catalogue; sellers operate within it. I enforced this strictly.

3. **Coupon proportional allocation** — The first implementation applied the full discount to the first eligible line item. That's incorrect — if a ₹200 cart coupon applies to 3 line items, the discount needs to be distributed proportionally by line subtotal. I rewrote this logic.

4. **Unnecessary features** — AI generated a "Permissions" admin tab, a notification service stub, and some other things that added noise. I removed them. The backend already enforces role-based access through Spring Security filters — a fake UI tab doesn't add value.

## Trade-offs I made (and why I'm comfortable with them)

**PostgreSQL search projection instead of Elasticsearch** — I modeled a `product_search_documents` table that mirrors what an Elasticsearch document would look like. The future upgrade path is clear: swap the repository implementation. But for a demo, asking the reviewer to run an ES cluster is friction I didn't want.

**JWT with server-side persistence** — Stateless JWTs are popular, but they can't be revoked without a blacklist. I store tokens server-side so logout actually works. It's a pragmatic choice for a demo where you're logging in and out of different roles.

**One coupon per cart** — Real platforms support stacking, bank offers, seller-funded discounts. I kept it to one coupon because the proportional allocation logic across eligible lines is already non-trivial, and I wanted that logic to be correct and explainable rather than covering every edge case badly.

**Thymeleaf + vanilla JS instead of React** — I wanted the reviewer to run one Spring Boot app, not deal with a separate frontend build. The UI is functional and demonstrates real workflows — it's not a design showcase.

**Product versioning via JSON snapshots** — Every product update stores a snapshot. The last 50 versions are kept. This enables rollback without a full audit infrastructure. It's simple and it works.

## What the app actually does

**Customer flow:** Browse categories and products without login → click a product to see details, attributes, stock status, and eligible coupons → login/signup when adding to cart → manage cart items (qty 0 removes) → apply one coupon and see real-time discount breakdown → checkout (validates stock → reserves inventory → creates order atomically) → view order history.

**Seller flow:** Signup/login → set up profile and warehouses → create products under leaf categories (form dynamically renders attribute fields from admin-defined metadata) → publish/unpublish products → update inventory per warehouse → enroll products into eligible coupons → view and fulfill order lines for owned products only.

**Admin flow:** Login (seeded, no signup) → manage category hierarchy with collapsible tree view → define typed attributes (text, number, select, boolean) on leaf categories with constraints → create/edit coupons with discount type, scope, date range, and category eligibility → monitor coupon lifecycle (scheduled → live → expired).

**Security:** Every API enforces authorization server-side. Public endpoints are explicitly marked. Cart/order APIs derive the customer from the JWT principal. Seller APIs verify product/warehouse/order-line ownership. Admin APIs require the PRODUCT_ADMIN role. Even if someone bypasses the UI, the backend rejects unauthorized requests.

---