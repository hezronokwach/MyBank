# MyBank

MyBank is a full-stack digital banking application. A **Spring Boot 3 REST API** handles users, bank accounts and money movement (deposits, withdrawals, transfers), and a **React + Vite** single-page app gives customers and administrators a web interface on top of it.

It covers the core concerns of a small core-banking system: JWT authentication, role-based access, PIN-protected transactions, row-locked transfers, and idempotent transfer requests.

It also has an **AI assistant** (Spring AI + Groq) that answers questions about the customer's own accounts and prepares changes, such as freezing an account or sending money, that only happen after the customer confirms them in the app. See [AI assistant](#ai-assistant).

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Domain model](#domain-model)
- [Business rules](#business-rules)
- [AI assistant](#ai-assistant)
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
- Confirm the email address through a link (sent with Brevo) before signing in for the first time.
- Log in and receive a JWT (valid for 24 hours by default).
- Open additional accounts: `SAVINGS`, `CURRENT`, `FIXED_01`, `FIXED_02`, `FIXED_03`, in any 3-letter currency.
- Deposit, withdraw and transfer money, each authorised with the account PIN.
- See balances and projected monthly interest per account.
- Update an account's phone number.
- Transfer ownership of an account to another registered user.
- Close (delete) an account.
- See the last transactions of each account on the dashboard.
- Ask the AI assistant about balances and transactions, and have it prepare a freeze or a transfer that they confirm on a card.

**Administrators**
- Approve new accounts and unfreeze frozen ones from the **Account approvals** page.
- Change status and verification `tier` through the API, and an account's `type` from the page.
- See how the AI assistant is used on the **Assistant stats** page: answers, failures, tokens, estimated cost, blocked attempts, confirm cards and feedback.

**Platform**
- Stateless JWT authentication (HS-signed, `role` claim embedded).
- BCrypt hashing for both passwords and PINs.
- Method-level role checks (`@EnableMethodSecurity` + `@PreAuthorize`).
- Transactional money movement with pessimistic row locks, taken in a fixed order so two opposite transfers can't deadlock.
- `Idempotency-Key` header on transfers so retries never double-charge.
- Centralised, uniform JSON error responses.
- H2 in-memory database for development, PostgreSQL for production.
- A default admin user seeded on startup.
- Spring Boot Actuator: a public health check and admin-only metrics.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.3 (Web, Data JPA, Security, Validation) |
| Auth | Spring Security + JJWT 0.12.6 |
| AI | Spring AI 1.0 (OpenAI starter) pointed at Groq, model `openai/gpt-oss-120b` |
| Monitoring | Spring Boot Actuator + Micrometer |
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
│   ├── assistant/                  # AI assistant: service, tools, pending actions, guard, metrics
│   ├── config/                     # SecurityConfig, DataLoader (seeds admin)
│   ├── controller/                 # AuthController, AccountController
│   ├── dto/requests|responses/     # Request/response records
│   ├── entity/                     # User, Account, Transaction, IdempotencyKey
│   ├── enums/                      # AccountStatus, AccountTier, AccountType, ...
│   ├── exceptions/                 # Domain exceptions + GlobalExceptionHandler
│   ├── mappers/                    # AccountMapper, TransactionMapper, UserMapper
│   ├── repository/                 # JPA repositories
│   ├── security/                   # JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetailsService
│   ├── services/ (+ impl/)         # AccountService, TransactionService, TransferService, TransactionQueryService, UserService
│   └── util/                       # AccountNumberGenerator, ReferenceGenerator
├── src/main/resources/
│   ├── application.properties      # Active profile (dev by default), AI and Actuator settings
│   ├── prompts/assistant-v*.txt    # Versioned system prompts for the assistant
│   ├── application-dev.properties  # Local database, JWT and Groq key (git-ignored — create it yourself)
│   └── application-prod.properties # PostgreSQL config driven by env vars
├── src/test/resources/eval/        # The assistant's evaluation questions
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
- Both accounts are locked (`PESSIMISTIC_WRITE`) in ascending account-number order, so two opposite transfers cannot deadlock.
- The source account must belong to the caller; the PIN is checked against the source account.
- Rejected when: same source and destination, either account not `ACTIVE`, source is a fixed account, currencies differ, amount ≤ 0, or insufficient balance.

### PIN-protected account actions
Deleting an account and transferring ownership both require the account PIN.

## AI assistant

Customers open the **Need help?** button on the Dashboard and ask about their own accounts in English or Swahili. The assistant runs on Groq (`openai/gpt-oss-120b`) through Spring AI's OpenAI starter, since Groq speaks the OpenAI API format.

The model never touches the database. It can only call six Java **tools**, and every tool is bound to the customer from the JWT, never to anything typed in the chat:

| Tool | What it does | Changes anything? |
|---|---|---|
| `listMyAccounts` | The customer's accounts: masked number, type, currency, balance, status | No |
| `getAccount` | One account, found by its last 4 (or more) digits or its full number | No |
| `getRecentTransactions` | Up to 20 recent transactions on one account | No |
| `findTransaction` | One transaction by reference (`TRN…`, `DEP…`, `WDR…`) | No |
| `requestFreezeAccount` | Prepares a freeze, shown as a **confirm card** | Only after the customer presses Confirm |
| `requestTransfer` | Prepares a transfer, shown as a **confirm card** with a PIN box | Only after the customer types the PIN and presses Confirm |

There are deliberately **no admin tools**. The model can't change status or tiers, unfreeze accounts, or see other customers. That's why the admin pages have no assistant.

### Architecture

```
 React chat panel                          Spring Boot                                         Groq
┌────────────────────┐ POST /assistant/chat ┌─────────────────────────────────────────────┐   ┌──────────────┐
│ Need help? > ask   │ ───────────────────▶ │ AssistantService (one turn)                 │   │ gpt-oss-120b │
│                    │                      │  1. RateLimiter: 10 messages/min per user   │   │              │
│ answer             │                      │  2. load conversation + last 10 messages    │   │              │
│ "Based on: ..."    │ ◀─────────────────── │  3. save the question (PINs masked)         │   │              │
│ thumbs up / down   │                      │  4. ChatClient: prompt vN + history + tools │◀─▶│  tool calls  │
│                    │                      │        │                                    │   └──────────────┘
│ ┌────────────────┐ │                      │        ▼                                    │
│ │ Confirm card   │ │                      │     AssistantTools (bound to the JWT user)  │
│ │ PIN [____]     │ │                      │      - at most 5 calls per answer           │
│ │ Confirm/Cancel │ │                      │      - an audit row for every call          │
│ └───────┬────────┘ │                      │      - request* tools save a PendingAction  │
└─────────┼──────────┘                      │  5. OutputGuard cleans the answer           │
          │                                 │  6. save the answer, link its tool calls    │
          │                                 └─────────────────────────────────────────────┘
          │ POST /assistant/actions/{id}/confirm  (no AI involved)
          └───────────────────────────────▶ PendingActionService ─▶ AccountService / TransferService
```

The model call has a 10-second budget with no retries, and no database transaction stays open while it runs.

### Confirm cards (human in the loop)

1. A `request…` tool only saves a **pending action** (`PENDING`, expires in 5 minutes). Nothing changes yet.
2. The answer shows a card such as "Freeze account ...4821" or "Send KES 500.00 from ...4821 to ...7777".
3. Confirm calls `POST /api/v1/assistant/actions/{id}/confirm` directly. The AI is not involved in this step.
4. The action is marked `CONFIRMED` before anything runs. A `@Version` column means two confirms at the same moment can't both run.
5. A transfer goes through the normal `TransferService` with all its checks, using `assistant-<action id>` as the idempotency key, so the money moves once even on a double click.
6. **The PIN** for a transfer is typed only on the card and sent only to the confirm endpoint. It never enters the chat or reaches the model, and it is never saved or logged. A wrong PIN marks the card `FAILED`, and that card can't be retried, so a PIN can't be guessed by pressing Confirm again and again.
7. Cards nobody confirms become `EXPIRED` (a scheduled job runs every minute).

### Controls

| Control | How it works |
|---|---|
| **Guardrails** | Tools only ever see the logged-in customer's accounts; another customer's account looks exactly like one that doesn't exist. No admin tools. At most 5 tool calls per answer, 500 characters per message, 10 messages a minute per customer. The system prompt keeps it to the customer's own banking questions. Security lives in code, not in the prompt. |
| **Human in the loop** | Every change is a confirm card the customer must approve within 5 minutes. Transfers also need the PIN. |
| **Fallback** | On a timeout, a Groq error or an empty answer, the customer gets "The assistant isn't available right now…" with links to the deposit, withdraw and transfer pages. Groq's own usage limit is recorded separately (`RATE_LIMITED_PROVIDER`). |
| **Audit** | Every message, with its prompt version, model, tokens, latency and outcome (`assistant_messages`). Every tool call, with its arguments, outcome and duration, linked to the answer it was for (`assistant_tool_calls`). Every pending action, with its result and when it was decided (`assistant_pending_actions`). |
| **Explainability** | Each answer shows "Based on: your accounts, recent transactions…", built from the tool calls that actually succeeded. |
| **Output checks** | `OutputGuard` cleans every answer, and every question before it's saved: runs of 8 or more digits become `...` plus the last 4, "PIN 1234" becomes "PIN ****", and login tokens are removed. The frontend also strips markdown bold marks. |
| **Evaluation** | Security tests with no AI run with the normal test suite. A 20-question evaluation runs by hand against the real model (see below). |
| **Monitoring** | Micrometer metrics at `/actuator/metrics` (admin only), the **Assistant stats** page, and one structured log line per answer (ids, outcome, latency, tokens, prompt version, tool names; never the message text). |
| **Data minimisation** | The model only sees masked account numbers, and never names, emails, phone numbers or PIN hashes. Questions are saved with PINs masked. Metric tags never hold message text, account numbers or user ids. |

### Data it stores

| Table | Holds |
|---|---|
| `assistant_conversations` | One row per chat: owner and title |
| `assistant_messages` | Each question and answer, with prompt version, model, tokens, latency and outcome |
| `assistant_tool_calls` | Each tool call: tool, arguments, outcome, duration, and the answer it belongs to |
| `assistant_pending_actions` | Freeze and transfer requests: status, expiry and result (never the PIN) |
| `assistant_feedback` | Thumbs up or down, and an optional comment, per answer |

### Prompt versions and evaluation

The system prompt lives in `src/main/resources/prompts/assistant-<version>.txt` and is picked by `app.assistant.prompt-version`. The version is saved with every answer, so results can be compared.

| Version | Change |
|---|---|
| `v1` | First prompt |
| `v2` | Rule 4: if the customer shares a PIN, the reply starts by warning them never to share it |
| `v3` (active) | v2 plus preparing transfers, with the PIN typed only on the confirm card |

The evaluation (`AssistantEvaluationTest`) asks 20 questions from `src/test/resources/eval/assistant-cases.csv`: 8 read, 4 action, 6 safety and 2 Swahili. It checks which tools were called and what the reply must and must not say, then saves the results to `target/eval-results/`. It fails if any safety case fails. It only runs when asked:

```bash
./mvnw test -Dtest=AssistantEvaluationTest -Deval=true -Deval.runs=3
```

Results with `openai/gpt-oss-120b`, 3 runs of each prompt back to back:

| | Prompt v1 | Prompt v2 |
|---|---|---|
| Wrong answers | 3: no PIN warning (2), picked an account instead of asking which (1) | **0** |
| Questions Groq didn't answer | 0 | 9, all at the end of run 3 (Groq's daily limit) |
| Score | 57 of 60 | 51 of 60 |
| Average latency | 1.5 s | 1.2 s |

v2 answered correctly every question Groq replied to, including the PIN warning v1 missed. Its lower score comes only from Groq's free daily limit running out. Neither prompt leaked another customer's data or a full account number. **v3 has not been evaluated yet.**

### Monitoring

| Metric | Tags |
|---|---|
| `assistant.requests` | `outcome` (`OK`, `FALLBACK`, `RATE_LIMITED`, `RATE_LIMITED_PROVIDER`) |
| `assistant.latency` | `outcome` |
| `assistant.tokens` | `type` (`input`, `output`) |
| `assistant.cost.estimate` | none (US dollars at Groq's paid price; the free tier costs nothing) |
| `assistant.tool.calls` | `tool`, `outcome` |
| `assistant.blocked` | `reason` (`rate_limit`, `tool_limit`, `output_guard`) |
| `assistant.actions` | `type`, `status` |
| `assistant.feedback` | `rating` (`up`, `down`) |

`/actuator/health` is public and only returns `{"status":"UP"}`. Everything else under `/actuator` needs an admin. The **Assistant stats** page (`/admin/stats`) shows the same numbers through `GET /api/v1/admin/assistant/metrics`. They are kept in memory and start from 0 after each restart.

### Production notes

- **Use Flyway migrations, not `ddl-auto=update`.** Hibernate's automatic update adds tables and columns but never changes or removes them. For example, on PostgreSQL it created the `type` column of `assistant_pending_actions` with a check that only allowed `FREEZE_ACCOUNT`, so adding transfers needed a manual `ALTER TABLE assistant_pending_actions DROP CONSTRAINT IF EXISTS assistant_pending_actions_type_check;`. In production every schema change would be a versioned Flyway migration, with `ddl-auto=validate`.
- The rate limiter and the metrics are kept in memory. That's fine for one instance; several instances would need a shared store (for example Redis) and a metrics backend such as Prometheus.
- Groq's free plan allows about 8,000 tokens a minute and has a daily limit. A busy deployment needs a paid plan.

## Getting started

### Prerequisites
- JDK 21
- Node.js 20+ and npm (for the frontend)
- Docker (optional)

### 1. Configure the dev profile

`application-dev.properties` is git-ignored, so create `src/main/resources/application-dev.properties`:

```properties
# H2 in-memory database (Spring picks the driver from the URL, so the tests can use H2 too)
spring.datasource.url=jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
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

# AI assistant: a free key from https://console.groq.com (optional; without it the assistant
# replies "The assistant isn't available right now" and the rest of the app works)
spring.ai.openai.api-key=<your-groq-key>
```

To use PostgreSQL locally instead (for example a Neon database), set `spring.datasource.url=jdbc:postgresql://<host>/<db>?sslmode=require` with its username and password, and leave out the H2 console lines.

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

The suite runs on an in-memory database and never calls Groq: the model is replaced by a mock. The real-model evaluation is separate, see [Prompt versions and evaluation](#prompt-versions-and-evaluation).

## Configuration

| Property / env var | Profile | Description |
|---|---|---|
| `spring.profiles.active` | all | `dev` by default; set `SPRING_PROFILES_ACTIVE=prod` in production. |
| `DATABASE_URL` | prod | JDBC URL, e.g. `jdbc:postgresql://host:5432/mybank`. |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | prod | Database credentials. |
| `JWT_SECRET` → `app.jwt.secret` | all | Base64 HMAC signing key. |
| `app.jwt.expiration` | all | Token lifetime in ms (default `86400000` = 24 h). |
| `FRONTEND_URL` → `app.react-url` | all | Allowed CORS origin (defaults to `http://localhost:5173`). |
| `GROQ_API_KEY` → `spring.ai.openai.api-key` | all | Groq key for the AI assistant. Optional: without it the assistant falls back to "isn't available right now". |
| `ASSISTANT_MODEL` | all | Groq model (default `openai/gpt-oss-120b`). |
| `app.assistant.prompt-version` | all | Which `prompts/assistant-<version>.txt` to use (default `v3`). |
| `app.assistant.price.input-per-million` / `output-per-million` | all | US dollars per million tokens for the cost estimate (default `0.15` / `0.60`). |
| `management.endpoints.web.exposure.include` | all | Actuator endpoints that are switched on (`health,metrics`). |
| `BREVO_API_KEY` → `app.mail.brevo-api-key` | all | Brevo key for the confirmation emails. Without it, locally the email is printed in the backend log; in `prod`, sign-up fails until it is set. |
| `MAIL_FROM` → `app.mail.from` | all | The sender address, verified in Brevo. Required when `BREVO_API_KEY` is set. |
| `MAIL_FROM_NAME` → `app.mail.from-name` | all | The sender name (default `MyBank`). The confirmation link points at `FRONTEND_URL`. |
| `VITE_API_BASE_URL` | frontend | API base URL baked into the frontend build. |

## API reference

Base URL: `/api/v1`. All endpoints except `/auth/register`, `/auth/login`, `/auth/verify-email` and `/auth/resend-verification` require `Authorization: Bearer <token>`.

### Authentication

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/register` | Create a user and their default account, and email a confirmation link. Returns `{ email, message }`, no JWT. `503` if the email can't be sent (nothing is saved). |
| `POST` | `/auth/verify-email` | `{ token }` from the link. Confirms the email. A wrong, used or expired link (24 hours) gets `400`. |
| `POST` | `/auth/resend-verification` | `{ email }`. Sends a new link, at most 3 an hour. Always the same answer, so it can't reveal who has an account. |
| `POST` | `/auth/login` | Authenticate. Returns a JWT and the user's first account. An unconfirmed email gets `403` "Confirm your email first" (only after the right password). |
| `GET` | `/auth/me` | The signed-in user's own first name, last name and email. Needs a token. |
| `GET` | `/auth/me/role` | Returns `ROLE_USER` or `ROLE_ADMIN` for the current token. Needs a token. |

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

**Login response**
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
| `GET` | `/accounts/{accountNumber}/transactions?limit=5` | user | — | The account's most recent transactions (at most 20). |
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

### AI assistant

| Method | Path | Role | Body | Description |
|---|---|---|---|---|
| `POST` | `/assistant/chat` | user | `{ "conversationId"?, "message" }` | Ask a question (at most 500 characters). Leave out `conversationId` to start a new chat. Returns the answer, its sources and any confirm cards. `429` after 10 messages in a minute. |
| `POST` | `/assistant/actions/{id}/confirm` | user | `{ "pin" }` for a transfer, nothing for a freeze | Run a prepared action. |
| `POST` | `/assistant/actions/{id}/cancel` | user | — | Cancel a prepared action. |
| `POST` | `/assistant/feedback` | user | `{ "messageId", "rating": 1 or -1, "comment"? }` | Thumbs up or down on an answer. |
| `GET` | `/admin/assistant/metrics` | admin | — | Totals for the Assistant stats page. |

Actuator (outside `/api/v1`): `GET /actuator/health` is public; `GET /actuator/metrics` and `/actuator/metrics/{name}` need an admin.

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
| Bean validation failure, malformed JSON, insufficient balance, currency mismatch, self-transfer, transfer card confirmed without a PIN | `400` |
| Bad credentials or wrong PIN | `401` |
| No token, wrong role, non-positive amount, operation not allowed for a fixed account | `403` |
| User, account, conversation or confirm card not found (or not owned by caller), account not active | `404` |
| Duplicate email, database constraint violation, confirm card already decided or expired | `409` |
| More than 10 assistant messages in a minute | `429` |
| Anything unexpected | `500` |

## Frontend

The React app in [`mybank-frontend/`](mybank-frontend) stores the JWT in `localStorage` and attaches it to every request through an Axios interceptor ([`src/api/axiosInstance.js`](mybank-frontend/src/api/axiosInstance.js)).

| Route | Page |
|---|---|
| `/` | Landing page |
| `/register`, `/login` | Sign-up (then "Check your email") and sign-in |
| `/verify-email` | Opened from the link in the confirmation email |
| `/dashboard` | Customer accounts overview, recent activity, account options and the assistant (**Need help?**) |
| `/admin` | Account approvals: approve new accounts, unfreeze frozen ones, change type |
| `/admin/stats` | Assistant stats |
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
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod -e DATABASE_URL=jdbc:postgresql://host:5432/mybank -e DATABASE_USERNAME=mybank -e DATABASE_PASSWORD=change-me -e JWT_SECRET=<base64-secret> -e FRONTEND_URL=https://your-frontend.example.com -e GROQ_API_KEY=<groq-key> mybank-api
```

The Dockerfile builds the jar with Maven (`-DskipTests`) and runs it on `eclipse-temurin:21-jre`. Any container host with a managed PostgreSQL (Render, Railway, Fly.io, etc.) works.

### Frontend (Vercel)

Set the project root to `mybank-frontend`, add `VITE_API_BASE_URL=https://<your-api>/api/v1` as an environment variable, and deploy. `vercel.json` rewrites all paths to `index.html` so client-side routes work on refresh. Remember to set the backend's `FRONTEND_URL` to the Vercel domain for CORS.

## Known limitations and roadmap

- **Seeded admin password** is hard-coded in `DataLoader` and shown on the home page; move it to an environment variable before sharing a public link.
- **Schema changes** use Hibernate `ddl-auto=update`; production should use Flyway migrations (see [Production notes](#production-notes)).
- **PIN format** is only length-checked when opening extra accounts; registration accepts any non-blank PIN.
- **Balance check** uses `balance > amount`, so an account cannot be drained to exactly zero.
- **Idempotency keys** are global, not scoped per user, and never expire.
- **Interest** is informational only — no scheduler credits it, and the message uses `$` regardless of currency.
- **The admin account list** has no owner name or email.
- **The assistant's rate limiter and metrics** live in memory, so they reset on restart and aren't shared between instances.
- **Prompt v3** has not been through the model evaluation yet.

## License

Released under the [MIT License](LICENSE). © 2026 Hezron Okwach.
