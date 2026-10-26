# MyBank

MyBank is a full-stack digital banking application. A **Spring Boot 3 REST API** handles users, bank accounts and money movement (deposits, withdrawals, transfers), and a **React + Vite** single-page app gives customers and administrators a web interface on top of it.

It covers the core concerns of a small core-banking system: JWT authentication, role-based access, PIN-protected transactions, ordered account lookups for transfers, and idempotent transfer requests.

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Domain model](#domain-model)
- [Business rules](#business-rules)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [API reference](#api-reference)
- [Error handling](#error-handling)
- [Frontend](#frontend)
- [Deployment](#deployment)
- [Known limitations and roadmap](#known-limitations-and-roadmap)
- [License](#license)

---

## Features

**Customers**
- Register with email, password and a 4-digit transaction PIN — a default `CURRENT` account in `KES` is opened automatically.
- Log in and receive a JWT (valid for 24 hours by default).
- Open additional accounts: `SAVINGS`, `CURRENT`, `FIXED_01`, `FIXED_02`, `FIXED_03`, in any 3-letter currency.
- Deposit, withdraw and transfer money, each authorised with the account PIN.
- See balances and projected monthly interest per account.
- Update an account's phone number.
- Transfer ownership of an account to another registered user.
- Close (delete) an account.

**Administrators**
- View every account in the bank.
- Approve/freeze/close accounts (`status`) and upgrade verification `tier`.
- Change an account's `type`.

**Platform**
- Stateless JWT authentication (HS-signed, `role` claim embedded).
- BCrypt hashing for both passwords and PINs.
- Transactional money movement, with transfer accounts loaded in a consistent order (groundwork for deadlock-free locking).
- `Idempotency-Key` header on transfers so retries never double-charge.
- Centralised, uniform JSON error responses.
- H2 in-memory database for development, PostgreSQL for production.
- A default admin user seeded on startup.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.3 (Web, Data JPA, Security, Validation) |
| Auth | Spring Security + JJWT 0.12.6 |
| Database | H2 (dev), PostgreSQL (prod) |
| Build | Maven (wrapper included) |
| Frontend | React 19, React Router 7, Axios, Tailwind CSS 4, Vite 8 |
| Packaging | Docker (multi-stage), Vercel (frontend) |

## Architecture

```
┌──────────────────────┐        HTTPS / JSON         ┌─────────────────────────────────────────┐
│  React SPA (Vite)    │  Authorization: Bearer JWT  │           Spring Boot API               │
│  mybank-frontend/    │ ──────────────────────────▶ │                                         │
│  axiosInstance.js    │                             │  JwtAuthenticationFilter                │
└──────────────────────┘                             │        │                                │
                                                     │        ▼                                │
                                                     │  Controllers  (/api/v1/auth, /accounts) │
                                                     │        │   DTOs + Bean Validation       │
                                                     │        ▼                                │
                                                     │  Services (business rules, @Transactional)
                                                     │        │   Mappers (entity ⇄ DTO)       │
                                                     │        ▼                                │
                                                     │  Spring Data JPA repositories           │
                                                     └────────┬────────────────────────────────┘
                                                              ▼
                                                    H2 (dev) / PostgreSQL (prod)
```

The backend follows a classic layered design:

- **controller** — thin REST endpoints; read the authenticated user's email from the `Authentication` principal and delegate.
- **dto** — Java `record` request/response types with Jakarta validation annotations.
- **services / services.impl** — all business rules, transactions and locking.
- **repository** — Spring Data JPA interfaces (`findAndLock…` finders are intended for row locking).
- **mappers** — convert entities to response DTOs (and compute interest info).
- **security** — JWT creation/validation and `UserDetailsService`.
- **exceptions** — domain exceptions extending `BaseException` (each carries its HTTP status) plus a `@RestControllerAdvice` handler.
- **config** — security configuration and the startup `DataLoader`.

## Project structure

```
MyBank/
├── pom.xml                         # Maven build
├── Dockerfile                      # Multi-stage image (Maven build → JRE 21 runtime)
├── src/main/java/com/example/mybank/
│   ├── MyBankApplication.java
│   ├── config/                     # SecurityConfig, DataLoader (seeds admin)
│   ├── controller/                 # AuthController, AccountController
│   ├── dto/requests|responses/     # Request/response records
│   ├── entity/                     # User, Account, Transaction, IdempotencyKey
│   ├── enums/                      # AccountStatus, AccountTier, AccountType, ...
│   ├── exceptions/                 # Domain exceptions + GlobalExceptionHandler
│   ├── mappers/                    # AccountMapper, TransactionMapper, UserMapper
│   ├── repository/                 # JPA repositories
│   ├── security/                   # JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetailsService
│   ├── services/ (+ impl/)         # AccountService, TransactionService, TransferService, UserService
│   └── util/                       # AccountNumberGenerator, ReferenceGenerator
├── src/main/resources/
│   ├── application.properties      # Selects the active profile (dev by default)
│   ├── application-dev.properties  # Local H2 config (git-ignored — create it yourself)
│   └── application-prod.properties # PostgreSQL config driven by env vars
└── mybank-frontend/                # React SPA
    ├── src/api/axiosInstance.js    # Axios client + JWT interceptor
    ├── src/pages/                  # Route-level pages
    ├── src/components/             # Forms, layout, notifications
    └── vercel.json                 # SPA rewrite rules for Vercel
```

## Domain model

```
User (users)                      Account (accounts)                   Transaction (transactions)
─────────────                     ──────────────────                   ──────────────────────────
user_id      UUID PK      1 ──< N id             UUID PK         ┌──── from_account_id  FK (null for deposits)
email        unique               account_number 10 digits, unique│┌─── to_account_id    FK (null for withdrawals)
password_hash (BCrypt)            user_id        FK → users       ││   reference          DEP/WDR/TRN + 7 digits
first_name, last_name             balance        decimal          ││   amount             decimal(19,4)
role  ROLE_USER | ROLE_ADMIN      currency       3 chars          ││   type               DEPOSIT | WITHDRAWAL | TRANSFER
created_at                        pin            BCrypt hash      ││   status             SUCCESS | FAILED | PENDING
                                  type, status, tier              ││   created_at
                                  phone_number, created_at  ◀─────┘┘

IdempotencyKey (idempotency_keys): id, idempotency_key (unique), response_body (JSON), created_at
```

Transactions have no setters — they are an append-only ledger. Schema is generated by Hibernate (`ddl-auto=update`).

### Enumerations

| Enum | Values | Meaning |
|---|---|---|
| `AccountStatus` | `PENDING_VERIFICATION`, `ACTIVE`, `FROZEN`, `CLOSED` | New accounts start pending; only `ACTIVE` accounts can transact. |
| `AccountTier` | `TIER_1_UNVERIFIED`, `TIER_2_VERIFIED` | KYC level, set by an admin. |
| `AccountType` | `SAVINGS`, `CURRENT`, `FIXED_01`, `FIXED_02`, `FIXED_03` | Determines interest and allowed operations. |
| `TransactionType` | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER` | |
| `UserRole` | `ROLE_USER`, `ROLE_ADMIN` | |

## Business rules

### Account lifecycle
1. Every new account (on registration or via `POST /accounts`) is created with balance `0`, status `PENDING_VERIFICATION` and tier `TIER_1_UNVERIFIED`.
2. An administrator must set the status to `ACTIVE` (`PATCH /accounts/{n}/status`) before the account can deposit, withdraw or transfer.
3. Account numbers are 10 digits: the prefix `100` followed by 7 random digits from `SecureRandom`.

### Interest (informational)
Each account response includes an `interestInfo` string with the projected monthly interest (`balance × annual rate ÷ 12`), paid on the day of the month the account was opened:

| Type | Annual rate |
|---|---|
| `SAVINGS` | 7% |
| `FIXED_01` | 4% |
| `FIXED_02` | 5% |
| `FIXED_03` | 8% |
| `CURRENT` | none |

Interest is calculated for display only; no scheduled job credits it yet.

### Deposits
- Account must belong to the caller, PIN must match, status must be `ACTIVE`, amount must be > 0.
- Fixed accounts (`FIXED_*`) accept **exactly one** deposit.

### Withdrawals
- Same ownership, PIN, status and amount checks.
- Not allowed from fixed accounts.
- Balance must be strictly greater than the amount.

### Transfers
- Requires an `Idempotency-key` header. If the key was seen before, the stored response is returned and nothing is moved again.
- Both accounts are loaded in ascending account-number order, so once row locks are enabled two opposite transfers cannot deadlock (see [Known limitations](#known-limitations-and-roadmap)).
- The source account must belong to the caller; the PIN is checked against the source account.
- Rejected when: same source and destination, either account not `ACTIVE`, source is a fixed account, currencies differ, amount ≤ 0, or insufficient balance.

### PIN-protected account actions
Deleting an account and transferring ownership both require the account PIN.

## Getting started

### Prerequisites
- JDK 21
- Node.js 20+ and npm (for the frontend)
- Docker (optional)

### 1. Configure the dev profile

`application-dev.properties` is git-ignored, so create `src/main/resources/application-dev.properties`:

```properties
# H2 in-memory database
spring.datasource.url=jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# JWT — use a random Base64 string of at least 256 bits
app.jwt.secret=<your-base64-secret>
app.jwt.expiration=86400000
app.react-url=http://localhost:5173
```

### 2. Run the backend

```bash
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd spring-boot:run`. The API listens on `http://localhost:8080`, and the H2 console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:bankdb`).

On startup `DataLoader` creates an administrator if one does not exist:

| Email | Password |
|---|---|
| `admin@mybank.com` | `adminpassword` |

Change this password (or the seed) before deploying anywhere public.

### 3. Run the frontend

```bash
cd mybank-frontend
npm install
npm run dev
```

The app opens on `http://localhost:5173` and calls `http://localhost:8080/api/v1` unless `VITE_API_BASE_URL` is set.

### 4. Run the tests

```bash
./mvnw test
```

## Configuration

| Property / env var | Profile | Description |
|---|---|---|
| `spring.profiles.active` | all | `dev` by default; set `SPRING_PROFILES_ACTIVE=prod` in production. |
| `DATABASE_URL` | prod | JDBC URL, e.g. `jdbc:postgresql://host:5432/mybank`. |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | prod | Database credentials. |
| `JWT_SECRET` → `app.jwt.secret` | all | Base64 HMAC signing key. |
| `app.jwt.expiration` | all | Token lifetime in ms (default `86400000` = 24 h). |
| `FRONTEND_URL` → `app.react-url` | all | Allowed CORS origin (defaults to `http://localhost:5173`). |
| `VITE_API_BASE_URL` | frontend | API base URL baked into the frontend build. |

## API reference

Base URL: `/api/v1`. All endpoints except `/auth/register` and `/auth/login` require `Authorization: Bearer <token>`.

### Authentication

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/register` | Create a user and their default account. Returns a JWT. |
| `POST` | `/auth/login` | Authenticate. Returns a JWT and the user's first account. |
| `GET` | `/auth/me/role` | Returns `ROLE_USER` or `ROLE_ADMIN` for the current token. |

**Register**
```json
POST /api/v1/auth/register
{
  "email": "jane@example.com",
  "password": "supersecret",
  "firstName": "Jane",
  "lastName": "Doe",
  "pin": "1234"
}
```

**Auth response**
```json
{
  "token": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "email": "jane@example.com",
  "account": {
    "accountNumber": "1004829173",
    "balance": 0,
    "currency": "KES",
    "type": "CURRENT",
    "status": "PENDING_VERIFICATION",
    "tier": "TIER_1_UNVERIFIED"
  }
}
```

### Accounts

| Method | Path | Role | Body | Description |
|---|---|---|---|---|
| `GET` | `/accounts/me` | user | — | List the caller's accounts. |
| `GET` | `/accounts/{accountNumber}` | user | — | Get one of the caller's accounts. |
| `POST` | `/accounts` | user | `{ "type", "currency", "pin" }` | Open a new account. |
| `PATCH` | `/accounts/{accountNumber}/details` | user | `{ "phoneNumber" }` | Update contact details. |
| `PATCH` | `/accounts/{accountNumber}/transfer` | user | `{ "newOwnerEmail", "pin" }` | Transfer ownership. |
| `DELETE` | `/accounts/{accountNumber}` | user | `{ "pin" }` | Close and delete the account. |
| `GET` | `/accounts` | admin | — | List all accounts. |
| `PATCH` | `/accounts/{accountNumber}/status` | admin | `{ "status", "tier" }` | Change status and/or tier. |
| `PATCH` | `/accounts/{accountNumber}/type` | admin | `"SAVINGS"` | Change account type. |

**Account response**
```json
{
  "accountNumber": "1004829173",
  "balance": 1500.00,
  "currency": "KES",
  "phoneNumber": "+254700000000",
  "type": "SAVINGS",
  "status": "ACTIVE",
  "tier": "TIER_2_VERIFIED",
  "createdAt": "2026-09-10T05:30:40Z",
  "interestInfo": "You will get $8.75 as interest on day 10 of every month"
}
```

### Money movement

| Method | Path | Body | Description |
|---|---|---|---|
| `PATCH` | `/accounts/{accountNumber}/deposits` | `{ "amount", "pin" }` | Deposit into an owned account. |
| `PATCH` | `/accounts/{accountNumber}/withdrawals` | `{ "amount", "pin" }` | Withdraw from an owned account. |
| `PATCH` | `/accounts/transfers` | `{ "fromAccountNumber", "toAccountNumber", "amount", "pin" }` | Transfer between accounts. Header `Idempotency-key: <uuid>` is required. |

**Transfer example**
```bash
curl -X PATCH http://localhost:8080/api/v1/accounts/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-key: 6f1c2a9e-1d3b-4c7e-9a51-1f0b2e3d4c5a" \
  -H "Content-Type: application/json" \
  -d '{"fromAccountNumber":"1004829173","toAccountNumber":"1007718264","amount":250.00,"pin":"1234"}'
```

**Transaction response**
```json
{
  "reference": "TRN4829173",
  "type": "TRANSFER",
  "status": "SUCCESS",
  "amount": 250.00,
  "accountNumber": "1004829173",
  "amountPaid": 250.00,
  "createdAt": "2026-09-10T05:31:02Z"
}
```

## Error handling

`GlobalExceptionHandler` turns every failure into the same JSON shape:

```json
{
  "timeStamp": "2026-09-10T05:31:02.123",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient balance",
  "path": "/api/v1/accounts/transfers"
}
```

| Situation | Status |
|---|---|
| Bean validation failure, malformed JSON, insufficient balance, currency mismatch, self-transfer | `400` |
| Bad credentials or wrong PIN | `401` |
| Non-positive amount, operation not allowed for a fixed account | `403` |
| User or account not found (or not owned by caller), account not active | `404` |
| Duplicate email, database constraint violation | `409` |
| Anything unexpected | `500` |

## Frontend

The React app in [`mybank-frontend/`](mybank-frontend) stores the JWT in `localStorage` and attaches it to every request through an Axios interceptor ([`src/api/axiosInstance.js`](mybank-frontend/src/api/axiosInstance.js)).

| Route | Page |
|---|---|
| `/` | Landing page |
| `/register`, `/login` | Sign-up and sign-in |
| `/dashboard` | Customer accounts overview |
| `/admin` | Admin dashboard (approve accounts, change tier/type) |
| `/create-account` | Open a new account |
| `/deposit`, `/withdraw`, `/transfer` | Money movement |
| `/transfer-ownership` | Hand an account to another user |
| `/delete-account` | Close an account |

Scripts: `npm run dev`, `npm run build`, `npm run preview`, `npm run lint`.

## Deployment

### Backend (Docker)

```bash
docker build -t mybank-api .
```

```bash
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod -e DATABASE_URL=jdbc:postgresql://host:5432/mybank -e DATABASE_USERNAME=mybank -e DATABASE_PASSWORD=change-me -e JWT_SECRET=<base64-secret> -e FRONTEND_URL=https://your-frontend.example.com mybank-api
```

The Dockerfile builds the jar with Maven (`-DskipTests`) and runs it on `eclipse-temurin:21-jre`. Any container host with a managed PostgreSQL (Render, Railway, Fly.io, etc.) works.

### Frontend (Vercel)

Set the project root to `mybank-frontend`, add `VITE_API_BASE_URL=https://<your-api>/api/v1` as an environment variable, and deploy. `vercel.json` rewrites all paths to `index.html` so client-side routes work on refresh. Remember to set the backend's `FRONTEND_URL` to the Vercel domain for CORS.

## Known limitations and roadmap

- **Role checks are not enforced yet.** Controllers use `@PreAuthorize`, but method security is not enabled (`@EnableMethodSecurity` is missing), so any authenticated user can currently call the admin endpoints. Add `@EnableMethodSecurity` to `SecurityConfig` to enforce them.
- **Self-assigned roles.** `RegisterRequest` accepts an optional `role` field and `UserMapper` honours it, so anyone can register as `ROLE_ADMIN`. Ignore the field and always assign `ROLE_USER`.
- **No row locking yet.** `AccountRepository.findAndLockByAccountNumber…` methods have no `@Lock(LockModeType.PESSIMISTIC_WRITE)` annotation, so Spring Data treats them as plain finders and concurrent requests can race on balances. Add the annotation to enable locking.
- **Seeded admin password** is hard-coded in `DataLoader`; move it to an environment variable.
- **PIN format** is only length-checked when opening extra accounts; registration accepts any non-blank PIN.
- **Balance check** uses `balance > amount`, so an account cannot be drained to exactly zero.
- **Idempotency keys** are global, not scoped per user, and never expire.
- **Interest** is informational only — no scheduler credits it, and the message uses `$` regardless of currency.
- **Transaction history** is stored but not exposed by any endpoint yet.
- Test coverage is limited to the Spring context smoke test.

## License

Released under the [MIT License](LICENSE). © 2026 Hezron Okwach.
