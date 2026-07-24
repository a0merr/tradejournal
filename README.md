# tradejournal
 
**A full-stack trade journal: ingests trading-bot fills into PostgreSQL through a Spring Boot REST API, with a normalized schema, versioned migrations, JWT authentication, and a React dashboard for reviewing positions and performance.**
 
[![CI](https://github.com/a0merr/tradejournal/actions/workflows/ci.yml/badge.svg)](https://github.com/a0merr/tradejournal/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=flat&logo=react&logoColor=black)](https://react.dev/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
 
> ⚠️ **Status: early development.** The design below describes the target. Sections marked _planned_ are not yet implemented, and the live-demo link is a placeholder until the app is deployed.
 
**Live demo:** deploy in one click via [`render.yaml`](render.yaml) — see [DEPLOY.md](DEPLOY.md) · **API docs:** `/swagger-ui` when running
 
---
 
## What this is
 
A trading bot generates a stream of fills — individual executions, each with a price, quantity, and fee. `tradejournal` is where those fills go to become reviewable: it ingests them through a REST API, stores them in a properly normalized PostgreSQL schema, reconstructs positions and performance from them, and surfaces it all in a React dashboard.
 
It's a deliberately realistic full-stack application — auth, migrations, an indexed relational schema, integration tests against a real database — built to look like something a team would actually run, not a tutorial to-do list.
 
---
 
## Tech stack
 
| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3 (Web, Data JPA, Security) |
| Database | PostgreSQL 16 |
| Migrations | Flyway (versioned SQL) |
| Auth | Short-lived JWT + rotating refresh tokens (Spring Security) |
| Frontend | React 18 + TypeScript (Vite) |
| Testing | JUnit 5, Testcontainers (integration tests on real Postgres) |
| Tooling | Docker Compose, OpenAPI / Swagger |
 
---
 
## Architecture
 
```
  React (Vite + TS)              Spring Boot
  ┌──────────────┐    HTTPS     ┌──────────────────────────────────┐
  │  dashboard   │ ───JWT────▶  │ Controller → Service → Repository │ ──▶ PostgreSQL
  │  + charts    │ ◀──JSON────  │      (REST)     (logic)  (JPA)    │      (Flyway-
  └──────────────┘              └──────────────────────────────────┘       managed)
                                              ▲
                                  trading bot │ POST /api/fills
                                  (ingest endpoint)
```
 
A classic layered backend: controllers handle HTTP and validation, services hold the business logic (position reconstruction, PnL), and repositories talk to Postgres through JPA. The schema is owned by Flyway, so the database is versioned in lockstep with the code.
 
---
 
## Database schema
 
The schema is the heart of this project, so it's normalized with intent. A single order can produce many fills (partial executions), instruments are reference data rather than repeated strings, and positions are *derived* from fills rather than stored as a mutable number that can drift.
 
```
users ──1:N──▶ accounts ──1:N──▶ orders ──1:N──▶ fills
                                    ▲
            instruments ──1:N───────┘
 
positions  =  a SQL view aggregating fills per (account, instrument)
```
 
| Table | Key columns | Notes |
|---|---|---|
| `users` | `id`, `email` (unique), `password_hash` | Auth principal |
| `refresh_tokens` | `id`, `user_id` → users, `token_hash`, `family_id`, `expires_at`, `revoked_at` | Single-use; only the SHA-256 is stored |
| `accounts` | `id`, `user_id` → users, `broker`, `base_currency` | A user may hold several |
| `instruments` | `id`, `symbol`, `exchange`, `asset_class` | Unique on `(symbol, exchange)` |
| `orders` | `id`, `account_id` → accounts, `instrument_id` → instruments, `side`, `type`, `quantity`, `status`, `external_id` | One per submitted order; `(account_id, external_id)` unique |
| `fills` | `id`, `order_id` → orders, `quantity`, `price`, `fee`, `filled_at` | Many per order; the raw event |
| `positions` | _view_ | Net quantity, total fees, and average fill price, computed from fills |
 
**Indexes & constraints** are chosen for the real query patterns: `fills(order_id)` and `fills(filled_at)` for journal queries and time-range scans, `orders(account_id, status)` for the open-orders view, a unique constraint on `instruments(symbol, exchange)` to keep reference data clean, and foreign keys throughout so the database — not the application — guarantees referential integrity. Money is stored as `NUMERIC`, never floating point.
 
All of this lives in versioned Flyway migrations under `backend/src/main/resources/db/migration/`, so the schema's history is auditable and every environment builds the same database.
 
---
 
## REST API
 
| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Obtain an access token + refresh token |
| `POST` | `/api/auth/refresh` | Rotate a refresh token into a new pair |
| `POST` | `/api/auth/logout` | Revoke the session |
| `POST` | `/api/fills` | **Ingest a fill** (the bot's entry point) — idempotent on `externalId` |
| `POST` | `/api/fills/import` | Bulk-import fills from a CSV ([`sample-fills.csv`](sample-fills.csv)) |
| `GET` | `/api/orders` | List orders, filterable by status, paginated (`page`, `size`) |
| `GET` | `/api/orders/{id}` | One order with its fills |
| `GET` | `/api/positions` | Current positions per account |
| `GET` | `/api/performance` | PnL, win rate, and exposure summary |
 
Protected endpoints require a `Bearer` token. Full request/response schemas are published via OpenAPI at `/swagger-ui` when the app is running.

### Ingestion is idempotent

A bot that times out and retries would otherwise book the same execution twice, and no later fix recovers the history — realized PnL is quietly wrong from then on. Send the broker's execution id as `externalId` and a repeat returns **200** with the fill already on file instead of **201** with a second copy. The guarantee is a partial unique index on `(account_id, external_id)`, so it holds under concurrent retries rather than only under polite ones; the losing insert is caught and resolved into the same 200.

The same key makes CSV re-imports safe. Uploading an overlapping broker statement reports the overlap as `skipped` rather than double-booking it.

### Two prices, because they answer different questions

- **`avgEntryPrice`** — FIFO cost basis of what is still open. The number to compare a market price against. Null once the position is flat.
- **`avgFillPrice`** — quantity-weighted average across every fill, buys and sells alike. An execution-quality figure.

The view originally exposed only the second one, named `avg_price`. After BUY 2 @ 100 and SELL 1 @ 120 it reported 106.67 for a position whose remaining unit was bought at 100 — a plausible-looking number that was not a basis at all. Cost basis needs lot-matching, which a `GROUP BY` cannot express, so it comes from the same FIFO replay that produces realized PnL. One accounting method, one set of books.

Related: **a closed trade is one closing fill, not one matched lot**. A single SELL that closes three entries is one trade, won or lost on its combined result — otherwise win rate drifts with how fragmented the entries happened to be.

### Auth: short access tokens, rotating refresh tokens

A stateless JWT cannot be revoked before it expires — that is the cost of not checking a database on every request. Rather than pay it back with a blacklist lookup, the access token's lifetime is cut to 15 minutes and session continuity moves to an opaque refresh token that *is* server state:

- **Single use.** Every `/api/auth/refresh` spends the presented token and returns a new one.
- **Theft is detectable.** Tokens are chained by `family_id`. Presenting an already-spent token means two parties hold the same secret, so the whole family is revoked and both are forced back to login.
- **A database leak is not a session leak.** Only the SHA-256 of each token is stored.
- **Logout is real.** `/api/auth/logout` revokes the chain server-side instead of only clearing browser storage.

### Errors: RFC 7807 everywhere

Every failure — validation, domain, and the ones the security filter chain raises before a controller is reached — returns `application/problem+json` with a stable `type` URN, `title`, `status`, `detail`, `instance`, and `timestamp`:

```json
{
  "type": "urn:tradejournal:problem:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "quantity: must be greater than 0",
  "instance": "/api/fills",
  "timestamp": "2026-06-01T10:00:00Z",
  "errors": [{ "field": "quantity", "message": "must be greater than 0" }]
}
```

An expired access token is tagged `urn:tradejournal:problem:token-expired`, which is how the frontend knows to refresh and retry rather than send the user back to the login screen.

### Rate limiting and bounded responses

The unauthenticated auth endpoints run behind a per-IP token bucket — BCrypt makes each guess expensive but nothing stops an attacker making them in parallel forever. Over-budget requests get a `429` with the same problem-document shape and a `Retry-After`. Continuous refill rather than a fixed window, since a window that resets on the hour lets an attacker spend a full budget on either side of the boundary. The buckets are per instance, so behind multiple replicas the effective limit multiplies by the replica count — the fix at that point is to move them to Redis.

`/api/orders` is paginated with a server-side maximum page size, sorted by `(created_at, id)` so rows cannot repeat or vanish between pages on a non-unique timestamp. The PnL replay streams its fills and clears the persistence context as it goes, so an account's history is bounded by open lots rather than by fill count.

### Money on the wire

JSON has no decimal type, so a JSON number is parsed as an IEEE-754 double by every browser. `NUMERIC` in Postgres and `BigDecimal` in Java would be undone at that last hop, so every `BigDecimal` is serialised as a JSON **string** and the exact decimal survives to the client. `MonetaryPrecisionTest` fails the build if a `float` or `double` ever appears on an entity or DTO.
 
---
 
## Run it locally
 
The whole stack comes up with one command via Docker Compose — Postgres, the Spring Boot API, and the React frontend:
 
```bash
git clone https://github.com/a0merr/tradejournal.git
cd tradejournal
docker compose up --build
```
 
- Frontend → http://localhost:5173
- API → http://localhost:8080
- Swagger UI → http://localhost:8080/swagger-ui
Flyway applies the migrations on first boot, so the database is ready with no manual setup. Seed data for a demo account is loaded automatically in the `dev` profile.
 
To run the pieces separately during development:
 
```bash
# backend (needs a local Postgres on :5432, or run `docker compose up db` first)
cd backend && mvn spring-boot:run
 
# frontend
cd frontend && npm install && npm run dev
```
 
> Prefer `./mvnw`? Generate the Maven wrapper once with `mvn -N wrapper:wrapper`.
 
---
 
## Project structure
 
```
tradejournal/
├── backend/                     # Spring Boot API
│   ├── src/main/java/com/amerritt/tradejournal/
│   │   ├── controller/          # REST endpoints
│   │   ├── service/             # business logic (positions, PnL)
│   │   ├── repository/          # Spring Data JPA
│   │   ├── model/               # entities + DTOs
│   │   ├── security/            # JWT filter + config
│   │   └── config/
│   │   └── resources/
│   │       ├── db/migration/    # Flyway: V1__init.sql, ...
│   │       └── application.yml
│   └── src/test/                # JUnit + Testcontainers
├── frontend/                    # React + Vite + TypeScript
│   └── src/
├── docker-compose.yml
└── README.md
```
 
---
 
## Testing
 
```bash
cd backend && mvn test    # requires Docker (Testcontainers spins up Postgres 16)
```
 
Integration tests run against a **real PostgreSQL instance via Testcontainers**, not an in-memory substitute — so migrations, constraints, and SQL behavior are exercised exactly as they'll run in production. Service-layer logic (position reconstruction, PnL) is covered by unit tests with the database mocked.
 
---
 
## Roadmap
 
- [x] Schema + Flyway migrations
- [x] Auth (register / login / JWT + refresh)
- [x] Fill ingestion endpoint
- [x] Orders + positions endpoints
- [x] React dashboard: positions table + exposure chart
- [x] Performance endpoint (exposure, fees, notional, realized PnL + win rate via FIFO lot-matching)
- [x] Testcontainers integration suite
- [x] CI (build + test on push)
- [x] Deploy config (Render Blueprint: db + API + static frontend)
- [ ] Live demo running on a hosted URL
- [x] Realized PnL + win rate (FIFO lot-matching of closing fills)
- [x] CSV import for fills (broker statements)
- [x] Refresh-token rotation with reuse detection + revocable sessions
- [x] RFC 7807 problem responses across the API, including the security filter chain
- [x] Idempotent ingestion keyed on the broker execution id (safe bot retries, safe CSV re-imports)
- [x] FIFO cost basis for open positions, separated from average fill price
- [x] Per-IP rate limiting on auth, pagination on orders, streamed PnL replay
- [ ] Refresh token in an httpOnly cookie (needs CSRF protection on the refresh endpoint)
- [ ] Per-(account, instrument) PnL snapshots advanced at ingest, with replay as reconciliation
- [ ] Currency on fills (today an account is implicitly single-currency)
---
 
## Deployment _(planned)_
 
Backend and Postgres deploy as containers; the frontend builds to static assets behind the API. A live demo link will go at the top of this README once it's up — for a full-stack portfolio piece, a working hosted demo is worth more than any amount of description.
 
---
 
## License
 
Released under the MIT License — see [LICENSE](LICENSE).
 
## Contact
 
**Andrew Merritt** — [GitHub](https://github.com/a0merr) · [LinkedIn](https://www.linkedin.com/in/andrew-merritt-ab425537a) · a0merr05@louisville.edu
