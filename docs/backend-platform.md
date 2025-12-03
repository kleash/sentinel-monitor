# Platform Service (Unified Backend)

## Modules
- **Implemented**
  - Ingestion: Kafka `events.raw` consumer + REST `/ingest`; writes `event_raw`; idempotent dedupe on `(source_system, source_event_id)`; publishes `events.normalized`; DLQ `events.dlq`.
  - Rule Config: `/workflows` CRUD; versioned graph persisted (`workflow`/`workflow_version`/`workflow_node`/`workflow_edge`); sets active version on create.
  - Rule Engine: resolves workflows by `workflowKey`, `workflowKeys`, or event type; guards duplicates; enforces ordering; clears/creates expectations per edges (supports absolute deadline strings); emits `rule.evaluated` with deltas and group hash; raises alerts on SLA/order violations; handles synthetic missed payloads.
  - Expectation Scheduler: configurable polling interval/limit; claims due expectations and emits structured `synthetic.missed` with dedupe key; disabled via `ruleengine.scheduler-enabled`.
  - Aggregation: consumes `rule.evaluated`, adjusts in-flight/completed/late/failed counts per bucket; exposes aggregates and wallboard views.
  - Alerting: consumes `alerts.triggered`, upserts alerts with audit log; lifecycle endpoints for ack/suppress/resolve with actor and reason.
  - Read models: `/items/{correlationKey}` timeline (events, expectations, alerts); aggregates and wallboard endpoints; alert listing.
- **Remaining for functional completeness**
  - Engine: replay/recover endpoint; richer validation around workflow graph; pluggable severity policy.
  - Scheduler: shed-lock/leader guard + metrics and lag surfacing; operational dashboard wiring.
  - Aggregation: percentile latency/backlog calculations and group-dimension filters beyond hash; export-friendly views.
  - Alerting: notification adapters (email/webhook), maintenance windows application on ingest, bulk actions.
  - Observability: Kafka/DB health wired into `/actuator/health` details; tracing spans for rule transitions.

## Data Model (current)
- `event_raw`: raw ingest store with dedupe `(source_system, source_event_id)`, event timestamps, group dims JSON, payload JSON.
- `workflow`, `workflow_version`, `workflow_node`, `workflow_edge`: workflow definitions; versioned graphs.
- `workflow_run`, `event_occurrence` (includes `event_id`, late/order flags), `expectation` (status, `fired_at`): rule engine runtime.
- `stage_aggregate`: aggregation rollups (unique key per workflowVersion/groupHash/node/bucket).
- `alert`, `maintenance_window`, `audit_log`: alert lifecycle/audit scaffolding (audit populated on lifecycle changes).

## Endpoints (current)
- `POST /ingest` (roles: `operator`/`config-admin`): idempotent ingest, validates required fields, rate-limited.
- `GET /workflows`, `GET /workflows/{key}`, `POST /workflows` (roles: `viewer`/`config-admin` for read; `config-admin` for create).
- `GET /items/{correlationKey}` with optional `workflowVersionId` (roles: `viewer`/`operator`/`config-admin`): timeline of occurrences, expectations, alerts.
- `GET /workflows/{id}/aggregates?groupHash=&limit=` (roles: `viewer`/`operator`/`config-admin`): aggregates per workflow version.
- `GET /wallboard` (roles: `viewer`/`operator`/`config-admin`): latest aggregates across workflows for dashboard tiles.
- `GET /alerts?state=&limit=` (roles: `viewer`/`operator`/`config-admin`): list alerts.
- `POST /alerts/{id}/ack|suppress|resolve` (roles: `operator`/`config-admin`): lifecycle with optional `{reason, until}`; 404 when missing.
- Actuator: `/actuator/health`, `/actuator/prometheus`.

## Kafka Bindings
- Consume `events.raw` → normalize → `events.normalized`.
- Rule Engine consumes `events.normalized` + `synthetic.missed` (dedupe key).
- Aggregation consumes `rule.evaluated` (group hash inside payload); Alerting consumes `alerts.triggered`.
- Produce `synthetic.missed`, `rule.evaluated`, `alerts.triggered`, and DLQ `events.dlq` for invalid ingest.
- Message key: `correlationKey` (rule/alert topics) or node/edge dedupe for synthetic.

## Security
- OIDC JWT resource server, roles `viewer`, `operator`, `config-admin`.
- `/actuator/health` is public; others require auth.

## Developer Notes
- Module path: `backend/platform-service`.
- Flyway migrations: `V0001__create_event_raw.sql` through `V0006__rule_engine_enhancements.sql` (adds event ids, expectation fired_at, indexes).
- Profiles configurable via env vars (`DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `OIDC_ISSUER_URI`, scheduler knobs, etc.).
- Tests run with MariaDB Testcontainers and mocked Kafka, coverage gate >=70% via JaCoCo.

## Next Backend Work (priority)
1) Add replay/recovery API and guardrails on workflow graph validation.
2) Harden scheduler with lock/leader election + metrics/lag surfacing.
3) Extend aggregates with latency/backlog metrics and richer group dimension filters.
4) Wire notifications/maintenance windows into alerting; bulk lifecycle ops.
5) Expand regression/contract coverage (Kafka path) and surface observability (health/tracing).

## Frontend Guidance (interim)
- APIs available: `/ingest`, `/workflows`, `/items/{correlationKey}`, `/workflows/{id}/aggregates`, `/wallboard`, `/alerts` + lifecycle.
- All timestamps in UTC; UI should default to local display with UTC toggle.
