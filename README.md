# Banking System

A simulated, full-stack banking platform built to demonstrate clean architecture,
correct money handling (double-entry ledger), security, and a real deployment.

> ⚠️ Simulated bank — no real money. Cards/payments use **Stripe test mode** (added in Phase 4).

- **Backend:** Java 21, Spring Boot 3.5, Spring Security, JPA, PostgreSQL, Redis, Flyway, springdoc/OpenAPI
- **Frontend:** React 19 + TypeScript, Vite, Tailwind CSS, React Query, React Router
- **Infra:** Docker Compose (Postgres + Redis), GitHub Actions CI, Testcontainers

See [`PROJECT_PLAN.md`](./PROJECT_PLAN.md) for the full architecture and phased roadmap.

## Project status

- **Phase 0 — Scaffold & infra ✅**
- **Phase 1 — Auth & users ✅** — registration, login, JWT access + rotating
  refresh tokens (Redis), RBAC, KYC profiles, admin verification, signup/login/profile UI.
- **Phase 2 — Accounts & ledger ✅** (current) — open accounts, deposits/withdrawals
  on a **double-entry ledger** (balanced debit/credit entries, `BigDecimal`, pessimistic
  locking, running balance), a dashboard + account detail UI with transaction history.

Later phases add transfers, cards/payments, and loans.

### Accounts API (Phase 2)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/accounts` | bearer | Open an account (CHECKING/SAVINGS) |
| GET | `/api/v1/accounts` | bearer | List my accounts |
| GET | `/api/v1/accounts/{id}` | bearer | Get one account |
| POST | `/api/v1/accounts/{id}/deposit` | bearer | Deposit (amount) |
| POST | `/api/v1/accounts/{id}/withdraw` | bearer | Withdraw (amount, no overdraft) |
| GET | `/api/v1/accounts/{id}/transactions` | bearer | Transaction history (newest first) |

Every money movement posts a balanced set of ledger entries (debits + credits net to
zero) against the customer account and a system settlement account, in one DB transaction.

### Auth API (Phase 1)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/v1/auth/register` | public | Create a customer, returns tokens |
| POST | `/api/v1/auth/login` | public | Authenticate, returns tokens |
| POST | `/api/v1/auth/refresh` | public | Rotate refresh token → new pair |
| POST | `/api/v1/auth/logout` | public | Revoke a refresh token |
| GET | `/api/v1/users/me` | bearer | Current user + KYC profile |
| PUT | `/api/v1/users/me/profile` | bearer | Update KYC profile |
| GET | `/api/v1/admin/users` | ADMIN | List all customer profiles |
| POST | `/api/v1/admin/users/{id}/kyc` | ADMIN | Set KYC status |

**Demo admin** (seeded at startup): `admin@bank.local` / `Admin123!`
(override via `ADMIN_EMAIL` / `ADMIN_PASSWORD`).

## Prerequisites

- Java 21, Maven (the `./mvnw` wrapper is included), Node 20+, Docker.

## Run locally

```bash
# 1. (optional) create your env file
cp .env.example .env

# 2. start Postgres + Redis  (host port 5433 -> container 5432)
docker compose up -d postgres redis

# 3. run the backend
cd backend && ./mvnw spring-boot:run
#   http://localhost:8080/actuator/health   -> {"status":"UP"}
#   http://localhost:8080/swagger-ui.html   -> API docs

# 4. run the frontend (in another terminal)
cd frontend && npm install && npm run dev
#   http://localhost:5173  -> shows "Backend: UP"
```

> **Port note:** the Docker Postgres is published on host port **5433** to avoid
> clashing with a local Postgres on 5432. Inside Docker it remains 5432.

### Run the whole stack in Docker

```bash
docker compose --profile full up --build
```

## Test

```bash
cd backend && ./mvnw verify   # runs the Testcontainers smoke test (real Postgres)
cd frontend && npm run build  # type-check + production build
```

## Layout

```
backend/     Spring Boot (modular packages under com.bank.*)
frontend/    React + TypeScript (Vite)
docker-compose.yml
.github/workflows/ci.yml
PROJECT_PLAN.md
```
