# Sentinel Demo Guide

This guide describes how to launch a clean, demo-ready stack, what data is seeded, and how to drive live updates in the UI.

## Prerequisites
- Docker + Docker Compose
- Java 17+, Node 18+
- Free ports: `3306`, `29092/9092`, `8080`, `4300`

## One-Command Demo
```bash
./scripts/demo.sh
```
What it does:
- (Optionally) resets infra and volumes (`RESET_STATE=true` by default).
- Builds the backend jar (skip with `SKIP_BUILD=true`).
- Starts MariaDB + Kafka + backend (auth disabled by default for demos) + Angular UI in live-API mode (`npm run start:demo` via proxy).
- Seeds trade and equity workflows plus sample events.
- Verifies `/actuator/health` and `/wallboard`.

Key environment knobs:
- `BACKEND_PORT` / `FRONTEND_PORT` (defaults `8080` / `4300`)
- `SECURITY_DISABLE_AUTH` (default `true` for local demos; set to `false` to enforce JWT and pass `AUTH_TOKEN` to `seed.sh`)
- `RESET_STATE` (set to `false` to keep DB/Kafka volumes)
- `API_URL` (defaults to `http://localhost:${BACKEND_PORT}`)
- `API_PROXY_TARGET` (UI proxy target; defaults to backend URL)

## Seeded Workflows and Runs
- **Trade Lifecycle** (`trade-lifecycle`, group dims `book`, `region`)
  - `TR123` (EQD/NY) : ingest → two SYS2_VERIFIED → SYS3_ACK → SYS4_SETTLED (demonstrates expectedCount=2 and optional edge).
  - `TR456` (EQD/LN) : ingest + one SYS2_VERIFIED (one outstanding expectation remains).
  - `TR789` (EQD/NY) : ingest only, eventTime far in the past to trigger scheduler late/missed expectations.
- **Equity Allocation** (`equity-flow`, group dims `desk`, `region`)
  - `EQ-555` (EQD/NY) : alloc → validated → booked (clean run).
  - `EQ-556` (EQD/LN) : alloc only (in-flight to validation).
  - `EQ-557` (ETF/NY) : alloc (old) + late validation; booking expectation overdue so scheduler will emit a synthetic miss and alert.

Tip: `./scripts/seed.sh` is idempotent; rerun any time (requires backend up and `SECURITY_DISABLE_AUTH=true` or a valid bearer token via `AUTH_TOKEN`).

## Validations (API)
- Health: `curl -sf http://localhost:8080/actuator/health`
- Workflows and IDs: `curl -sf http://localhost:8080/workflows`
- Wallboard snapshot: `curl -sf http://localhost:8080/wallboard?limit=20`
- Timeline samples:
  - `curl -sf http://localhost:8080/items/TR123`
  - `curl -sf http://localhost:8080/items/TR789`
  - `curl -sf http://localhost:8080/items/EQ-557`

## UI Walkthrough (http://localhost:4300)
- **Wallboard**: shows trade/equity tiles; group filters by `book/region` or `desk/region`. `TR789` and `EQ-557` should show late/missed counts once the scheduler fires.
- **Workflow pages**: `/workflow/trade-lifecycle`, `/workflow/equity-flow` show stage tiles and alerts per workflow.
- **Item timelines**: `/item/TR123`, `/item/TR789`, `/item/EQ-557` expose hop-by-hop events, expectations, and alerts.
- **Alerts**: `/alerts` lists SLA/order violations; acknowledge/suppress/resolve actions work with auth disabled or a token that has `operator`/`config-admin`.

## Live Ingest During a Demo
Use these to show the wallboard updating in real time (adjust correlation keys as needed):

Send a new trade ingest:
```bash
curl -X POST http://localhost:8080/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"sys1",
    "eventType":"TRADE_INGEST",
    "eventTime":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "correlationKey":"TR-LIVE-001",
    "workflowKey":"trade-lifecycle",
    "group":{"book":"EQD","region":"NY"},
    "payload":{"notional":750000}
  }'
```

Advance that trade to SYS2 verification:
```bash
curl -X POST http://localhost:8080/ingest \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"sys2",
    "eventType":"SYS2_VERIFIED",
    "eventTime":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "correlationKey":"TR-LIVE-001",
    "workflowKey":"trade-lifecycle",
    "group":{"book":"EQD","region":"NY"}
  }'
```
Reload `/wallboard` or `/item/TR-LIVE-001` to show in-flight counts dropping and stage completions incrementing. If auth is enabled, add `-H "Authorization: Bearer <token>"` with roles `operator`/`config-admin`.

## Gaps / Notes
- Scheduler runs every 15s (`ruleengine.scheduler-interval-seconds`); allow ~20s after seeding for late/missed expectations to appear.
- Optional edges (e.g., `sys3-ack` → `sys4-settle`) do not create expectations; late alerts only arise for non-optional edges.
- Security is disabled by default for demos; set `SECURITY_DISABLE_AUTH=false` for realistic auth and supply valid JWTs. 
