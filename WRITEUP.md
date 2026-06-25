# Engineering Write-up

## 1. What I asked AI to do, and what I wrote or decided myself

I used AI as a pair-programming assistant for scaffolding and repetition-heavy code: creating DTOs, repositories, controller shells, test skeletons, README sections, service/controller documentation, Thymeleaf reviewer pages, and curl examples. I also used it to sanity-check edge cases around cart discount allocation, role boundaries, and inventory reservation flow.

The main design decisions were mine. I chose a modular monolith rather than microservices because the assignment is a 3-4 hour take-home and needs to be easy to run and explain. I also chose PostgreSQL as the source of truth, an outbox-style domain event table, and a search projection table that models the Elasticsearch/OpenSearch document shape without requiring another service to run. I decided to make roles explicit: `PRODUCT_ADMIN` owns category/coupon governance, `SELLER` owns products/warehouses/inventory/enrollments, and `CUSTOMER` owns cart/orders. I added separate `/admin`, `/seller`, and `/customer` Thymeleaf journeys only as reviewer aids; the UI still calls the same protected REST APIs with Bearer tokens.

## 2. Where I overrode, corrected, or threw away AI output

The initial AI-style backend was too flat and placed too much in one application class. I replaced it with proper Spring Boot layering: controller, service, repository, entity, DTO, enum, validation, mapper, config, exception, and security packages. I also removed raw string statuses and added enums for roles, product status, coupon status, discount type, discount scope, inventory reasons, reservation status, order status, payment status, fulfillment status, cart status, stock status, and event types. I added JavaDoc and reviewer documentation for controllers/services because the panel will likely judge whether the boundaries and trade-offs are easy to explain.

I corrected the earlier assumption that sellers should manage categories and coupons. In this version, Product Admin alone can create/update categories, sub-classifications, predefined category attributes, and coupon definitions. I also chose to upsert category attributes rather than delete-and-recreate them, because deleting old definitions can break existing product attribute rows. Sellers can only create products against those predefined attributes and enroll their own products into existing coupons.

I also changed inventory reservation. Instead of relying on an explicit application-level pessimistic lock for normal inventory updates, checkout uses a conditional database update: decrement only when `available_quantity >= requested_quantity`. This keeps the “last item cannot be sold twice” rule at the database write boundary, where PostgreSQL row-level locking and transaction isolation can enforce it.

## 3. Biggest trade-offs and alternatives considered

The first trade-off was search. The ideal production design would use Kafka plus Elasticsearch/OpenSearch. I kept PostgreSQL as the source of truth and implemented a denormalized `product_search_documents` projection table instead. The search package is deliberately isolated so the repository can later be replaced with Elasticsearch without changing product, cart, or order code. This keeps the demo runnable with only Docker Compose.

The second trade-off was authentication. I used simple opaque Bearer tokens stored in the database instead of JWT/OAuth. JWT would be more common at scale, but opaque tokens make logout/revocation simple and visible for a demo. The code still cleanly separates authentication from business services.

The third trade-off was coupon flexibility. I implemented a concise model: `FLAT` and `UPTO_PERCENT_OFF`, plus category eligibility and seller product enrollment. This is enough to demonstrate real-world behavior like a product participating in multiple coupons while the cart applies only one. I did not build a full promotion-rule DSL because that would add complexity without improving the core evaluation areas.

## 4. What is missing or what I would do with another day

With another day I would replace the PostgreSQL search projection with a real Elasticsearch/OpenSearch container and a Kafka-backed sync service. I would also add idempotency keys to order placement, a real payment authorization step, order cancellation and reservation release APIs, tax calculation, item-level returns/refunds, admin audit logs, and a notification service for email/SMS/in-app events.

I would also expand integration tests using Testcontainers for PostgreSQL so the conditional inventory update and Flyway migrations are tested against the same database engine used in Docker. I would add a small Selenium/Playwright smoke test for the Thymeleaf journeys as well. The current tests cover controller wiring and important service rules, but Testcontainers would give stronger confidence for concurrency and migration behavior.
