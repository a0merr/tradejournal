# tradejournal
 
**A full-stack trade journal: ingests trading-bot fills into PostgreSQL through a Spring Boot REST API, with a normalized schema, versioned migrations, JWT authentication, and a React dashboard for reviewing positions and performance.**
 
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
| Auth | JWT (stateless, Spring Security) |
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
| `accounts` | `id`, `user_id` → users, `broker`, `base_currency` | A user may hold several |
| `instruments` | `id`, `symbol`, `exchange`, `asset_class` | Unique on `(symbol, exchange)` |
| `orders` | `id`, `account_id` → accounts, `instrument_id` → instruments, `side`, `type`, `quantity`, `status` | One per submitted order |
| `fills` | `id`, `order_id` → orders, `quantity`, `price`, `fee`, `filled_at` | Many per order; the raw event |
| `positions` | _view_ | Net quantity + avg price, computed from fills |
 
**Indexes & constraints** are chosen for the real query patterns: `fills(order_id)` and `fills(filled_at)` for journal queries and time-range scans, `orders(account_id, status)` for the open-orders view, a unique constraint on `instruments(symbol, exchange)` to keep reference data clean, and foreign keys throughout so the database — not the application — guarantees referential integrity. Money is stored as `NUMERIC`, never floating point.
 
All of this lives in versioned Flyway migrations under `backend/src/main/resources/db/migration/`, so the schema's history is auditable and every environment builds the same database.
 
---
 
## REST API
 
| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Obtain a JWT |
| `POST` | `/api/fills` | **Ingest a fill** (the bot's entry point) |
| `POST` | `/api/fills/import` | Bulk-import fills from a CSV ([`sample-fills.csv`](sample-fills.csv)) |
| `GET` | `/api/orders` | List orders, filterable by account/status |
| `GET` | `/api/orders/{id}` | One order with its fills |
| `GET` | `/api/positions` | Current positions per account |
| `GET` | `/api/performance` | PnL, win rate, and exposure summary |
 
Protected endpoints require a `Bearer` token. Full request/response schemas are published via OpenAPI at `/swagger-ui` when the app is running.
 
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
- [x] Auth (register / login / JWT)
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
---
 
## Deployment _(planned)_
 
Backend and Postgres deploy as containers; the frontend builds to static assets behind the API. A live demo link will go at the top of this README once it's up — for a full-stack portfolio piece, a working hosted demo is worth more than any amount of description.
 
---
 
## License
 
Released under the MIT License — see [LICENSE](LICENSE).
 
## Contact
 
**Andrew Merritt** — [GitHub](https://github.com/a0merr) · [LinkedIn](https://www.linkedin.com/in/andrew-merritt-ab425537a) · a0merr05@louisville.edu
