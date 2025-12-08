# Sentinel - Event-Based Monitoring

Production-strength, Kafka-first event observability: model workflows as graphs, detect missing/late/out-of-order events, and surface live wallboards, alerts, and timelines for NOC/ops. Stack: Spring Boot backend (ingest -> rule engine -> expectations/scheduler -> aggregation -> alerting) plus Angular 20 command-center UI; MariaDB for state; Kafka-first ingest with REST fallback; UTC everywhere with durable timers.

## Why this platform
- Graph-based monitoring: configurable lifecycle graphs with SLAs and ordering rules.
- Durable expectations: DB-backed timers emit synthetic misses to avoid silent drops.
- Ops-ready UX: wallboard, workflow drill-down, item timelines, alert console, and ingest simulator.
- Enterprise hygiene: OIDC/JWT, DLQs, idempotent ingest, structured logging, metrics, and Playwright/karma/JUnit coverage.

## Repository Map
- `backend/platform-service` - Spring Boot service covering ingest, rule config/engine, expectation scheduler, aggregation, and alerting (see [docs/backend-platform.md](docs/backend-platform.md)).
- `frontend` - Angular portal with wallboard, workflow drill-down, item timeline, alerts console, rules authoring, and ingest simulator; mock API on by default ([docs/frontend-implementation.md](docs/frontend-implementation.md)).
- `tests/regression` - Playwright regression suite; bootstraps infra + backend via scripts and serves the mock-enabled UI; artifacts under `tests/regression/test-results` ([docs/regression-playwright.md](docs/regression-playwright.md)).
- `scripts` - `start.sh` / `stop.sh` / `teardown.sh` lifecycle helpers plus `seed.sh` for demo workflows/events ([docs/getting-started-portal.md](docs/getting-started-portal.md)).
- `ops/docker-compose.yml` - Local MariaDB + Kafka/ZooKeeper stack used by scripts.
- `docs` - Problem statement, architecture, delivery plan, platform/FE guides, regression notes.

## Quickstart (Local)
Prereqs: Docker + Compose, Java 17+, Node 18+, ports `3306/29092/9092/8080/4300` free.

**Fast path:** `./scripts/demo.sh` (reset infra, build backend, start stack with live backend UI, seed trade/equity data).  

Manual steps:
1) Start infra + backend + UI (mock-friendly):  
   `./scripts/start.sh`  
   Env knobs: `BACKEND_PORT`, `FRONTEND_PORT`, `DB_URL/DB_USER/DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `SKIP_FRONTEND=true`, `SKIP_BACKEND=true`, `FRONTEND_START_CMD` (override UI command).
2) Seed demo workflows/events (idempotent):  
   `./scripts/seed.sh` (`API_URL` to point at a different backend).
3) Visit UI `http://localhost:4300` (wallboard) and backend health `http://localhost:8080/actuator/health`.
4) Stop/clean: `./scripts/stop.sh` (keeps volumes) or `./scripts/teardown.sh` (removes Docker volumes).

## How to Run Tests
- Backend: `cd backend && ./mvnw test`
- Frontend: `cd frontend && npm test` (ChromeHeadless)
- Regression (Playwright): `cd tests/regression && npm test` (starts infra/backend via scripts, seeds data, serves UI)

## Key Docs & Flows
- [docs/architecture.md](docs/architecture.md) - Platform architecture and data model
- [docs/delivery-plan.md](docs/delivery-plan.md) - Delivery phases and milestones
- [docs/backend-platform.md](docs/backend-platform.md) - Backend implementation status
- [docs/frontend-implementation.md](docs/frontend-implementation.md) - Frontend implementation and data flow
- [docs/frontend-api.md](docs/frontend-api.md) - API guide for the UI gateway
- [docs/demo-guide.md](docs/demo-guide.md) - One-command demo, seeded data, and live ingest examples
- [docs/getting-started-portal.md](docs/getting-started-portal.md) - Portal onboarding, scripts, and feature tour
- [docs/regression-playwright.md](docs/regression-playwright.md) - Playwright regression details
- [docs/problem-statement.md](docs/problem-statement.md) - Problem statement and context

## Operational Notes
- Kafka-first ingest with REST `/ingest` fallback; idempotent handling and DLQ at `events.dlq`.
- Expectations/timers are durable in MariaDB; scheduler polls and emits `synthetic.missed` events to keep SLA alerts reliable.
- Use UTC for storage and SLA calculations; UI offers local/UTC toggle.
- Logs under `logs/`; PID files under `scripts/.pids/` when using `start.sh`.
