# Sentinel Command Center — Quickstart

This guide onboards production support and configuration teams to start, seed, configure, and operate the portal. It aligns with the architecture/delivery plan: Kafka + MariaDB + Spring backend + Angular frontend with PLC-style wallboard views.

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
_Tested_: start/stop/seed executed locally with confluent Kafka + MariaDB; backend/health OK; seed completed without errors.

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

## Configuration team guide
1) **Create/modify workflows**  
   - UI: `/rules` page supports nodes/edges, group dimensions, and runbook URL previewed on the graph canvas.  
   - API: `POST /workflows` (see `docs/frontend-api.md`) with nodes/edges; published immediately as active version.
2) **Ingest real events**  
   - Kafka-first: produce to `events.raw` with `correlationKey`, `eventType`, `eventTime`, `workflowKey` (hint).  
   - REST fallback: `POST /ingest` with same fields; include `Idempotency-Key` header.  
   - For demos, use `/ingest` UI form or `scripts/seed.sh`.
3) **Group dimensions & SLAs**  
   - Edges accept `maxLatencySec` or `absoluteDeadline`; nodes carry `eventType`.  
   - Group dimensions inform wallboard grouping (e.g., `book`, `region`, `feed`).  
4) **Runbooks**  
   - Set `runbookUrl` per workflow to surface links on alerts and detail pages.

## Production support guide
- **Monitor**: default landing `/wallboard`; drill into `/workflow/:key` and `/item/:correlationKey` from tiles/alerts.  
- **Act**: `/alerts` for ack/suppress/resolve; alert strip also exposes actions inline on workflow pages.  
- **Validate ingest**: `/ingest` for manual checks; `/rules` to verify active graph; `/items/{key}` API for raw timeline.  
- **Health**: backend `/actuator/health`, Prom metrics `/actuator/prometheus`; frontend availability at `/:port`.

## Debugging workflows & issues
- **Logs**: `logs/backend.log` (Spring/ingest/eval), `logs/frontend.log` (Angular dev server).  
- **Kafka**: ensure broker reachable at `KAFKA_BOOTSTRAP_SERVERS` (`localhost:29092` from host).  
- **Database**: MariaDB at `localhost:3306`, DB `sentinel` (root/sentinel defaults). Check tables `workflow_*`, `event_raw`, `alert`.  
- **UI**: inspect browser console for 401/5xx; Angular mock mode can be disabled via `frontend/src/environments/environment.ts` (`mockApi:false`).  
- **Seed validation**: after `seed.sh`, `/workflow/trade-lifecycle` should show `TR123` timeline and open alert (if SLA breached).

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
