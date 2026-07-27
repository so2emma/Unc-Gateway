# Unc — Kong-like Multi-Tenant API Gateway

Unc is a high-performance, self-hosted, multi-tenant API Gateway built with Java 21, Spring Boot 3.x, Spring WebFlux (reactive proxy engine), PostgreSQL, Redis, and two distinct Next.js web applications (Developer Portal & Operator Control Room Admin Dashboard).

---

## 🚀 Key Architectural Features

- **Reactive Proxy Engine (`gateway-core`)**: Fully non-blocking HTTP proxy engine built on Spring WebFlux and `WebClient` delivering ultra-low overhead routing.
- **Pluggable Filter Chain (`gateway-plugins`)**: Open, dynamically-sequenced plugin pipeline:
  1. `key-auth`: API key validation against consumer credentials.
  2. `rate-limit`: Redis ZSET sliding-window log algorithm for exact quota enforcement without boundary-burst flaws.
  3. `jwt-auth`: JWT signature verification and claim extraction.
  4. `request-transform`: Flexible header addition, removal, and mutation.
  5. `logging`: Non-blocking async request logger.
- **Zero-Downtime Dynamic Config (Postgres `LISTEN/NOTIFY`)**: Database triggers issue instant notifications upon modifications to services, routes, or plugin configs. `gateway-core` listens via a reactive R2DBC stream to invalidate and reload its in-memory route cache in <1 second without service restarts, backed by a 60s periodic reconciliation poll safety net.
- **Multi-Tenancy Model**: Shared-schema pool model using a `tenant_id` discriminator column across all tables (`tenants`, `services`, `routes`, `consumers`, `plugin_configs`, `request_logs`).
- **Developer Portal (`developer-portal`)**: Next.js App Router frontend designed with the **Blueprint** technical theme (`#F0F4F8`, `#16324F`, `#FF6A3D`, IBM Plex Mono) featuring interactive inline schematic endpoint cards (`Request` -> `Route` -> `Response`), self-serve consumer signup, and API key management.
- **Operator Control Room (`admin-dashboard`)**: Next.js App Router frontend designed with the **Control Room** dark theme (`#211F1E`, `#E8934A`, `#C1502E`, JetBrains Mono) featuring a live scrolling oscilloscope waveform traffic pulse, p95/p99 latency metrics tables, and dense operator CRUD management grids.

---

## 📁 Repository Structure

```
unc/
├── pom.xml                     # Maven root multi-module descriptor
├── gateway-plugins-api/        # Plugin interface contracts & PluginRegistry
├── gateway-plugins/            # Built-in plugins (key-auth, rate-limit, jwt-auth, request-transform, logging)
├── gateway-core/               # Reactive proxy engine + Postgres LISTEN/NOTIFY invalidator
├── admin-api/                  # Spring Boot CRUD REST API + Flyway migrations + DB NOTIFY triggers
├── analytics-api/              # Analytics ingestion endpoint & query API
├── developer-portal/           # Next.js App (Blueprint theme, self-serve keys, schematic cards)
├── admin-dashboard/            # Next.js App (Control Room dark theme, live traffic pulse)
├── mock-upstream/              # Mock HTTP upstream echo service for testing
└── docker-compose.yml          # Containerized local environment orchestrator
```

---

## ⚡ Quick Start with Docker Compose

Ensure Docker and Docker Compose are installed, then execute:

```bash
# 1. Package Java backend jars
mvn clean package -DskipTests

# 2. Launch all services via Docker Compose
docker compose up --build
```

### Exposed Service Endpoints

| Service | Host Port | Protocol / Description |
|---|---|---|
| **Gateway Core (Proxy)** | `http://localhost:8000` | Main proxied entrypoint for client traffic |
| **Admin API** | `http://localhost:8081` | REST CRUD management API |
| **Analytics API** | `http://localhost:8082` | Analytics ingestion & query API |
| **Developer Portal** | `http://localhost:3000` | Next.js Developer Portal (Blueprint theme) |
| **Admin Dashboard** | `http://localhost:3001` | Next.js Operator Control Room (Control Room theme) |
| **Mock Upstream** | `http://localhost:9090` | Upstream echo backend |
| **PostgreSQL** | `localhost:5432` | Main database (`unc_db`) |
| **Redis** | `localhost:6379` | Rate-limit cache store |

---

## 🧪 Testing the Gateway

### 1. Send Request Through Gateway Core
```bash
curl -i -X GET "http://localhost:8000/api/v1/echo/hello" \
  -H "X-API-Key: unc_key_demo12345"
```

### 2. Verify Rate Limiting
Execute requests in rapid succession to verify the sliding-window rate limit returns `HTTP 429 Too Many Requests` with `Retry-After` headers once quota is exceeded.

### 3. Verify Dynamic Route Reloading
Add a new route rule via the Admin Dashboard (`http://localhost:3001/routes`) or Admin API (`http://localhost:8081/api/admin/routes`). Observe `gateway-core` logs receiving the Postgres `NOTIFY` and reloading the route table in <1 second without restarting.

### 4. Run Automated Test Suite
```bash
mvn test
```
