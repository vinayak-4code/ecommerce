# One-page write-up

## 1. What I asked AI to do, and what I wrote or decided myself

I used AI to accelerate boilerplate generation, endpoint scaffolding, DTO definitions, and test skeletons. I explicitly directed the design toward a Java 21 Spring Boot/Gradle application with enterprise-style package structure, Spring Security, JPA repositories, validation, enums, HikariCP database pooling, Docker image build support, and separate seller/customer authentication flows.

The key design decisions I made were: keep this as a modular monolith instead of microservices, avoid an API Gateway, use simple Bearer-token authentication instead of JWT/OAuth for the demo, use PostgreSQL as the write source of truth, use an outbox table for domain events, and use a database-backed search projection that can later be replaced by OpenSearch/Elasticsearch.

## 2. Where I overrode, corrected, or threw away AI output

I rejected the earlier single-main-class style because it did not show enterprise judgment or maintainability. I split the code into controller, service, repository, DTO, entity, enum, validation, mapper, security, exception, and configuration packages.

I also avoided keeping important values as raw strings. Roles, product statuses, warehouse statuses, coupon types, discount scopes, order statuses, payment statuses, fulfillment statuses, event types, and notification channel types are enums.

I corrected the authentication model so signup/login clearly distinguishes seller and customer. Login now requires the expected role, which avoids ambiguity and protects role-specific flows.

I also replaced simplistic inventory logic with a pessimistic-lock reservation flow so the last available unit cannot be purchased twice.

## 3. Biggest trade-offs and alternatives considered

The first trade-off was modular monolith versus microservices. A microservice design would look closer to a large e-commerce platform, but it would be too heavy for a take-home demo. The modular monolith keeps deployment simple while preserving clear service boundaries.

The second trade-off was simple Bearer tokens versus JWT/OAuth. JWT would be more common for stateless services, but opaque persisted tokens are easier to reason about, revoke, test, and explain in a live extension session.

The third trade-off was a database-backed search projection versus real OpenSearch. The requirement points toward Elasticsearch/OpenSearch for high-read search, but running and testing that stack would add unnecessary operational overhead for a demo. I isolated the search projection behind a dedicated module so the persistence adapter can be replaced later.

## 4. What is missing or what I would do with another day

With another day I would add Testcontainers-based integration tests against PostgreSQL, replace the local search projection with OpenSearch, add a real Kafka outbox publisher, add refresh-token support, add idempotency keys for order placement, add payment integration boundaries, add shipment and return flows, and add more concurrency integration tests around inventory reservation.

I would also add API documentation using springdoc-openapi and more exhaustive tests around product rollback, product-level coupon allocation, partial fulfillment, and cancellation/refund calculations.
