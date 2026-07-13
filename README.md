<p align="center">
  <h1 align="center">🛒 VinCommerce</h1>
  <p align="center"><strong>Enterprise-Grade E-Commerce Platform</strong></p>
  <p align="center">
    A production-ready modular monolith built with Spring Boot 3 · Java 21 · PostgreSQL
  </p>
  <p align="center">
    <strong>Author:</strong> Vinayak Mittal &nbsp;·&nbsp;
    <a href="https://www.linkedin.com/in/vinayak-mittal-461b5a17b/">
      <img src="https://img.shields.io/badge/LinkedIn-Vinayak%20Mittal-blue?style=flat-square&logo=linkedin" alt="LinkedIn"/>
    </a>
  </p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5-green?style=flat-square&logo=springboot" alt="Spring Boot 3.5"/>
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql" alt="PostgreSQL 16"/>
  <img src="https://img.shields.io/badge/Docker-Ready-blue?style=flat-square&logo=docker" alt="Docker"/>
  <img src="https://img.shields.io/badge/Flyway-Migrations-red?style=flat-square&logo=flyway" alt="Flyway"/>
</p>

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Key Highlights](#-key-highlights)
- [Tech Stack](#-tech-stack)
- [Quick Start](#-quick-start)
- [Seeded Demo Data](#-seeded-demo-data)
- [Role-Based Features](#-role-based-features)
- [API Endpoints](#-api-endpoints)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Design Decisions](#-design-decisions)
- [Documentation](#-documentation)

---

## 🎯 Overview

**VinCommerce** is a multi-role e-commerce backend that demonstrates real-world enterprise patterns in a clean, reviewable codebase. It covers the full commerce lifecycle — from product catalogue governance to cart pricing to order fulfillment — with proper role isolation, inventory safety, and extensible architecture.

> **One command to run. Three roles to explore. Zero configuration needed.**

---

## ✨ Key Highlights

| # | Feature | Why It Matters |
|---|---------|----------------|
| 1 | **Modular Monolith** | Domain packages are independent and microservice-extractable |
| 2 | **Race-Condition-Safe Inventory** | Conditional SQL `WHERE available_qty >= :requested` prevents oversell |
| 3 | **Live Cart Pricing** | Prices recalculated from source-of-truth on every cart view |
| 4 | **Proportional Coupon Allocation** | Cart-level discounts distributed fairly across eligible order lines |
| 5 | **Product Versioning** | JSON snapshots (last 50) enable instant rollback without audit infra |
| 6 | **Domain Event Outbox** | Kafka-ready event pattern using DB outbox + in-process dispatch |
| 7 | **Search Projection** | Denormalized read model mirrors Elasticsearch document shape |
| 8 | **Flyway Migrations** | Schema + seed data versioned — reproducible from scratch every time |

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Java 21 (Records, Pattern Matching, Virtual Threads ready) |
| **Framework** | Spring Boot 3.5 + Spring Security |
| **Database** | PostgreSQL 16 + Flyway migrations |
| **ORM** | Spring Data JPA / Hibernate 6 |
| **Pool** | HikariCP (20 max, 5 min-idle) |
| **Auth** | JWT Bearer tokens (server-side stored, revocable) |
| **Build** | Gradle (Groovy DSL) |
| **Frontend** | Thymeleaf + Vanilla JS (SPA-like role dashboards) |
| **Containerization** | Docker + Docker Compose |
| **Code Gen** | Lombok |
| **Monitoring** | Spring Actuator (health, metrics, info) |

---

## 🚀 Quick Start

### Option A — Docker Compose (Recommended)

```bash
# Clone and run — that's it!
docker compose up --build
```

🌐 Open **http://localhost:8080** — the app is ready with seeded data.

### Option B — Local Development

**Prerequisites:** Java 21, Gradle 8.5+, PostgreSQL 16

```bash
# 1. Start PostgreSQL (via Docker or local install)
docker compose up postgres -d

# 2. Run the application
./gradlew bootRun
```

### Environment Variables (all optional — sensible defaults provided)

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/ecommerce` | Database URL |
| `DB_USERNAME` | `ecommerce` | DB user |
| `DB_PASSWORD` | `ecommerce` | DB password |
| `JWT_SECRET` | (configured) | Token signing secret |
| `TOKEN_EXPIRY_HOURS` | `12` | JWT lifetime |
| `SERVER_PORT` | `8080` | Application port |

---

## 🗂 Seeded Demo Data

The application ships with **pre-loaded data** via Flyway migrations — no manual setup required.

### 👤 Demo Accounts

| Role | Email | Password |
|------|-------|----------|
| 🔴 Product Admin | `admin@example.com` | `Password1` |
| 🟡 Seller | `seller@example.com` | `Password1` |
| 🟢 Customer | `customer@example.com` | `Password1` |

### 📦 Pre-loaded Catalogue

| Category | Example Products | Attributes |
|----------|-----------------|------------|
| 📱 Mobile Phones | Galaxy S24, iPhone 15, Pixel 8 | RAM, Storage, Display, Battery |
| 💻 Laptops | MacBook Pro, ThinkPad X1 | RAM, SSD, Processor, Screen Size |
| 📷 Cameras | Canon EOS R6, Sony A7 IV | Sensor, Megapixels, Lens Mount |
| 🎧 Accessories | AirPods Pro, Sony WH-1000XM5 | Type, Connectivity, Battery Life |
| 👕 Men's Apparel | Various | Size, Material, Fit |
| 👗 Women's Apparel | Various | Size, Material, Fit |

### 🏷️ Pre-loaded Coupons

| Code | Type | Description |
|------|------|-------------|
| `ELECTRO10` | Up to % off | 10% off electronics (max ₹500) |
| `FLAT200` | Flat discount | ₹200 off on min cart ₹1,500 |

### 🏭 Pre-loaded Inventory

All products have warehouse stock configured — ready for add-to-cart and checkout flows.

---

## 👥 Role-Based Features

### 🔴 Product Admin (`/admin`)

> Governs the catalogue structure and promotional rules.

| Capability | Details                                                                        |
|------------|--------------------------------------------------------------------------------|
| **Category Management** | Create root & nested categories                                                |
| **Attribute Definitions** | Define typed attributes (text, number, select, boolean) on leaf/sub categories |
| **Attribute Constraints** | Min/max, allowed values, required flag, customer visibility                    |
| **Coupon Creation** | Flat or percentage (with cap) discount types                                   |
| **Coupon Scoping** | Restrict by category, cart-wide, or product-specific                           |
| **Coupon Lifecycle** | Scheduled → Live → Expired based on date range                                 |

### 🟡 Seller (`/seller`)

> Manages their product portfolio, inventory, and fulfillment.

| Capability | Details                                                                    |
|------------|----------------------------------------------------------------------------|
| **Profile Management** | Business name, GST, contact details                                        |
| **Warehouse Setup** | Multiple fulfillment locations with address                                |
| **Product Creation** | Category-driven form with dynamic attribute fields                         |
| **Publish / Unpublish** | Control product visibility on storefront                                   |
| **Product Versioning** | Automatic snapshots; rollback to any previous version - Not added to UX/UI |
| **Inventory Updates** | Single or bulk stock updates per warehouse                                 |
| **Coupon Enrollment** | Opt-in products to admin-created coupons                                   |
| **Order Fulfillment** | View, ship, or cancel owned order lines                                    |

### 🟢 Customer (`/customer`)

> Shops, manages cart, applies coupons, and places orders.

| Capability | Details |
|------------|---------|
| **Public Browse** | View categories, products, and offers without login |
| **Product Search** | Filter by category, attributes, keyword; sort by price/name |
| **Product Details** | View attributes, stock status, and eligible coupon offers |
| **Cart Management** | Add/update/remove items (qty `0` removes); live-priced |
| **Coupon Application** | Apply one coupon; see real-time discount breakdown |
| **Checkout** | Validates stock → reserves inventory → places order atomically |
| **Order History** | View all orders with line-level detail and status |

---

## 🔌 API Endpoints

### Authentication
```
POST  /api/v1/auth/signup/seller       — Seller registration
POST  /api/v1/auth/signup/customer     — Customer registration
POST  /api/v1/auth/login               — Login (returns Bearer token)
POST  /api/v1/auth/logout              — Revoke token
```

### Catalogue (Admin)
```
GET   /api/v1/categories               — List categories (public)
POST  /api/v1/categories               — Create category + attributes
PUT   /api/v1/categories/{id}          — Update category
```

### Products (Seller)
```
POST  /api/v1/products                 — Create product
PUT   /api/v1/products/{id}            — Update product
POST  /api/v1/products/{id}/publish    — Publish to storefront
POST  /api/v1/products/{id}/unpublish  — Remove from storefront
POST  /api/v1/products/{id}/rollback/{v} — Rollback to version
DELETE /api/v1/products/{id}           — Delete product
```

### Inventory (Seller)
```
PUT   /api/v1/inventory                — Single stock update
PUT   /api/v1/inventory/bulk           — Bulk stock update
```

### Coupons (Admin + Seller)
```
POST  /api/v1/coupons                  — Create coupon (Admin)
GET   /api/v1/coupons                  — List coupons (Admin)
POST  /api/v1/coupons/{code}/products/{id}/enroll — Enroll product (Seller)
```

### Cart (Customer)
```
GET   /api/v1/cart                     — View cart (live-priced)
POST  /api/v1/cart/items               — Add/update item
POST  /api/v1/cart/coupons             — Apply coupon
DELETE /api/v1/cart/coupons            — Remove coupon
```

### Orders (Customer)
```
POST  /api/v1/orders                   — Place order from cart
GET   /api/v1/orders                   — List orders
GET   /api/v1/orders/{id}             — Order details
```

### Search (Public)
```
GET   /api/v1/search/products          — Full-text search with filters
```

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                             │
│   Browser (Thymeleaf + JS)  ·  curl / Postman  ·  Mobile App    │
└────────────────────────────────┬────────────────────────────────┘
                                 │ REST + Bearer Token
┌────────────────────────────────▼────────────────────────────────┐
│                      SPRING BOOT APPLICATION                     │
│                                                                  │
│  ┌──────────┐ ┌─────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐ │
│  │   Auth   │ │ Catalog │ │ Coupon │ │  Seller  │ │Inventory│ │
│  └──────────┘ └─────────┘ └────────┘ └──────────┘ └─────────┘ │
│  ┌──────────┐ ┌─────────┐ ┌────────┐ ┌──────────┐ ┌─────────┐ │
│  │   Cart   │ │  Order  │ │ Search │ │ Customer │ │  Common  │ │
│  └──────────┘ └─────────┘ └────────┘ └──────────┘ └─────────┘ │
│                                                                  │
│  Security Filter → Controller → Service → Repository → Entity   │
└────────────────────────────────┬────────────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────┐
│              PostgreSQL 16 + Flyway + HikariCP                   │
│         22 tables · indexes · constraints · seed data            │
└─────────────────────────────────────────────────────────────────┘
```

### Order Placement Flow (Atomic)

```
Cart → Reprice → Validate Stock → Reserve Inventory → Create Order
    → Snapshot Prices → Consume Reservation → Mark PLACED → Clear Cart
```

### Domain Event Flow

```
Business Service → DomainEventPublisher → outbox_events table
                                        → Spring ApplicationEvent
                                            → SearchIndexSyncService
                                            → (Future: Kafka producer)
```

---

## 📁 Project Structure

```
src/main/java/com/acme/ecommerce/
├── auth/           # Signup, login, logout, JWT token management
├── catalog/        # Categories, products, attributes, versioning
├── coupon/         # Definitions, discount strategies, enrollments
├── seller/         # Seller profiles, warehouses
├── inventory/      # Stock management, reservations, bulk updates
├── cart/           # Cart items, live pricing, coupon application
├── order/          # Order placement, lines, fulfillment tracking
├── search/         # Denormalized read projection (ES-shaped)
├── customer/       # Customer profile entity
├── common/         # Security config, exceptions, events, utilities
└── ui/             # Thymeleaf controllers for role dashboards

src/main/resources/
├── application.yml             # App configuration
├── db/migration/V1-V4.sql      # Schema + seed migrations
├── templates/                  # Thymeleaf HTML (12 pages)
└── static/                     # CSS + JavaScript
```

---

## 💡 Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Opaque tokens over stateless JWT** | Server-side storage enables instant revocation without blacklist infra |
| **Cart stores IDs only** | Prices computed live — never stale, always consistent with catalogue |
| **Conditional SQL for inventory** | `WHERE available_qty >= :qty` prevents oversell at DB level — no distributed locks |
| **Outbox event pattern** | Guarantees event delivery; trivial Kafka upgrade path |
| **Search projection in PostgreSQL** | Same document shape as Elasticsearch; swap later without business logic changes |
| **One coupon per cart** | Simplifies proportional discount allocation across lines |
| **Product JSON snapshots** | Last 50 versions stored — rollback without separate audit infrastructure |
| **Flyway for everything** | Schema, indexes, constraints, seed data — reproducible from zero |

---

## 📚 Documentation

| Document | Contents |
|----------|----------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System architecture, patterns, and trade-offs |
| [`docs/FRONTEND_UX.md`](docs/FRONTEND_UX.md) | Role-based UI/UX design rationale |
| [`docs/CLASS_GUIDE.md`](docs/CLASS_GUIDE.md) | Reviewer guide for major classes |
| [`docs/SERVICE_CONTROLLER_GUIDE.md`](docs/SERVICE_CONTROLLER_GUIDE.md) | Controller and service responsibility map |
| [`WRITEUP.md`](WRITEUP.md) | Take-home assessment write-up |

---

## 🐳 Docker Details

**Multi-stage Dockerfile:**
1. `gradle:8.5-jdk21` — builds fat JAR
2. `eclipse-temurin:21-jre-alpine` — minimal runtime (~180MB image)

**Compose services:**
- `postgres` — PostgreSQL 16 Alpine with healthcheck
- `app` — Spring Boot, waits for healthy DB
- Persistent volume: `ecommerce-postgres-data`

---