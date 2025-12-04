# Sentinel Command Center — Quickstart

This guide onboards a new operator or developer to start, stop, seed, and explore the portal without prior context. It aligns with the architecture and delivery plan: Kafka + MariaDB + Spring backend + Angular frontend with PLC-style wallboard views.

## Prerequisites
- Docker + Docker Compose (for Kafka/MariaDB bootstrap).
- Java 17+ and Node 18+ (for backend/frontend locally).
- Ports: `3306`, `29092/9092`, `8080`, `4300` free (override via env vars below).

## Scripts (root `scripts/`)
| Script | Purpose | Notes |
| --- | --- | --- |
| `./scripts/start.sh` | Boots infra (Docker), backend jar, and Angular dev server (mock-friendly). | Env: `BACKEND_PORT` (default `8080`), `FRONTEND_PORT` (default `4300`), `DB_URL`/`DB_USER`/`DB_PASSWORD` (default to local MariaDB), `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:29092`). Logs under `logs/`; PIDs under `scripts/.pids/`. |
| `./scripts/stop.sh` | Stops backend/frontend and pauses Docker containers. | Keeps volumes. |
| `./scripts/teardown.sh` | Full stop + `docker compose down -v` for infra. | Deletes infra volumes. |
| `./scripts/seed.sh` | Seeds demo workflows + events to showcase the UI. | Env: `API_URL` (default `http://localhost:8080`). Idempotent. |

### Start the stack
```bash
./scripts/start.sh
# wait for health: backend http://localhost:8080/actuator/health, frontend http://localhost:4300
```

### Stop or teardown
```bash
./scripts/stop.sh      # stops app processes, leaves infra data
./scripts/teardown.sh  # also removes Docker containers/volumes
```

### Seed demo data
Run after the backend is up:
```bash
./scripts/seed.sh
```
Seed contents:
- **Trade Lifecycle** workflow (ingest → sys2 verify → sys3 ack → settle) with sample trade `TR123` (EQD/NY) events.
- **Daily File Receipt** workflow (file received → validated → loaded) with `DAILY_FEED` sample.

## Feature tour (frontend)
- **Wallboard (`/wallboard`)**: workflow tiles with status pills, group metrics (in-flight/late/failed), countdown badges, wallboard/UTC toggles.
- **Workflow view (`/workflow/:key`)**: lifecycle graph (SVG canvas), stage tiles per node, group selector, alert strip with ack/suppress/resolve.
- **Item timeline (`/item/:correlationKey`)**: hop-by-hop events, pending expectations, linked alerts and countdowns.
- **Alerts console (`/alerts`)**: list, filter by state (mock backend returns all), lifecycle actions.
- **Rules (`/rules`)**: workflow catalog list and creation form (nodes/edges, group dimensions, runbook URL) with live graph preview.
- **Ingest simulator (`/ingest`)**: send sample events to `/ingest` endpoint; useful for ops smoke tests and demos.

## Configuration knobs
- Frontend mock mode defaults to **on** for offline demos (`frontend/src/environments/environment.ts`). Set `mockApi:false` and `apiBaseUrl/wsBaseUrl` when pointing at a real gateway.
- Ports: set `FRONTEND_PORT` / `BACKEND_PORT` before `start.sh`.
- Docker-less mode: if Docker isn’t installed, `start.sh` skips infra; ensure MariaDB/Kafka exist externally or disable backend start.

## Operational notes
- Logs live in `logs/` (`backend.log`, `frontend.log`).
- PID files in `scripts/.pids/` allow safe restarts; delete stale PIDs if you force-kill processes.
- Health: backend `http://localhost:8080/actuator/health`; frontend base `http://localhost:4300`.
- Tests (already run): `cd frontend && npm test`; `cd tests/regression && npm run test:coverage` (nyc-wrapped Playwright).

## Troubleshooting
- **Ports in use**: set `FRONTEND_PORT`/`BACKEND_PORT` then restart.
- **Docker not available**: start external MariaDB/Kafka, update backend config accordingly, or run frontend in mock mode only.
- **Seed failures**: ensure backend reachable at `API_URL` and DB migrations applied; rerun `seed.sh` (idempotent). 
