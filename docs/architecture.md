# Event-Based Monitoring System — Technical Design

## Context & Scope
- Goal: configurable event-driven monitoring with lifecycle graphs, timing SLAs, and actionable alerting for NOC/Prod Support; near real-time latency (sub-seconds to a few seconds).
- Scope: ingest heterogeneous events (Kafka primary, HTTP fallback), correlate by keys, evaluate workflow/rule graphs, maintain item/state/aggregate views, drive alerting/notifications, and surface via Angular UI.
- Constraints: Spring Boot microservices; Angular SPA; MariaDB primary store (partition/TTL/archival); event-driven decoupling between ingest and rule evaluation; at-least-once handling with idempotency.
- Non-goals: orchestrating business workflows; deep analytics warehouse; replacing existing paging tools.

## Architecture Overview
- Components: Ingestion Service, Rule Config Service, Rule Engine, Expectation Scheduler (timers), Aggregation Service, Alerting/Notification Service, UI Gateway/API, Angular SPA, MariaDB. Optional Keycloak/OIDC for auth.
- Messaging: Kafka topics for normalized events and synthetic misses; REST ingest path for Kafka-less sources (still published internally to Kafka); WebSocket push to UI; Spring Kafka listeners/producers with explicit topics; dead-letter topics for poison messages. Rule outcomes and alerts now stay in-process (no internal Kafka hop).
- Storage: MariaDB for raw events, workflow/rule metadata, item state, expectations/timers, aggregates, alerts, users/roles/audit; partitioned/TTL tables for raw/high-volume data.
- High-level flow (ingest → eval → alert → UI):
  - Producers → Kafka `events.raw` (primary) or REST `/ingest` for Kafka-less sources → Ingestion (validate/normalize/idempotent) → MariaDB `event_raw` + publish `events.normalized`.
  - Rule Engine consumes `events.normalized` → map to one or many workflows/rules (using workflowKey hint or eventType/group mapping) → update `item_state`/`workflow_run` → create/update expectations → emit rule evaluation + alerts in-process to aggregation/alerting.
  - Expectation Scheduler scans `expectation` table (due timers) → generate synthetic "expected-missed" events (in-process to rule engine; Kafka synthetic topic still accepted for external emitters) → Alerting.
  - Alerting consumes rule outcomes and SLA breaches in-process → dedupe/suppress → persist alert + send notifications.
  - Angular UI → UI Gateway (REST) → query state/aggregates/alerts, fetch graphs, issue ack/suppress commands.
- Component view (text):
  ```
  [Event Producers] -> Kafka(events.raw) -> [Ingestion]
     |                                   -> events.normalized -> [Rule Engine] -> [MariaDB state/expectations]
     |                                                                    |--> rule.evaluated / metrics
     |                                                                    v
     |                                                           [Aggregation Service]
     +---------------------------------------------------------------+
                                                                     v
                  [Expectation Scheduler] -> synthetic.missed -> [Rule Engine]
                                                                     v
                                                             [Alerting/Notification]
                                                                     ^
                                                                     |
                                                       Angular UI -> [UI Gateway/API]
  ```
- Key sequences:
- Ingest: Producer → Kafka `events.raw` (or REST `/ingest` when Kafka unavailable) → Ingestion validates schema, dedup by eventId + source → writes `event_raw` → emits `events.normalized`.
  - Rule eval: Rule Engine consumes normalized → find workflow definition by eventType/workflowKey → transition node; if out-of-order/dup mark accordingly → update `item_state` → create expectation rows with dueAt → publish metrics.
  - Timer breach: Expectation Scheduler selects due expectations → emit synthetic "expected_missed" event → Rule Engine marks stage late → Alerting raises/updates alert.
  - UI query: Angular → UI Gateway REST: `/workflows/{id}/graph`, `/workflows/{id}/groups/{g}/tiles`, `/items/{key}`, `/alerts?status=open`; responses served from aggregates/state tables and cached graph metadata.

## Service Design
- **Ingestion Service**
  - Responsibilities: consume Kafka `events.raw`; optional REST `/ingest` for edge cases; validate (JSON schema/Avro), normalize fields (timestamps to UTC, keys), enrich with source metadata; enforce idempotency; write `event_raw`; publish to `events.normalized`; route bad events to `events.dlq` via KafkaTemplate.
  - APIs: `POST /ingest` (idempotency-key header, payload includes eventType, eventTime, keys, groupDimensions, payload); health `/actuator/health`.
  - Topics: consume `events.raw`; produce `events.normalized`, `events.dlq`.
- **Rule Config Service**
  - Responsibilities: CRUD + versioning for workflows/rules/SLAs; validate graphs; dry-run with sample events; publish config change events.
  - APIs: `POST /workflows`, `PUT /workflows/{id}`, `GET /workflows/{id}/versions`, `POST /workflows/{id}:publish`, `POST /simulate` (payload: workflowVersionId, sampleEvents[]).
  - Topics: produce `config.workflow.updated`.
- **Rule Engine Service**
  - Responsibilities: consume normalized events and synthetic missed events; resolve workflow version(s) (fan-out allowed) via workflowKey hint and eventType/group mappings; apply ordering/dup rules; advance lifecycle; emit aggregates and evaluation outcomes; persist `item_state`, `workflow_run`, `event_occurrence`.
  - APIs: `GET /items/{key}` timeline; `GET /workflows/{id}/state?group=...`; `POST /replay` to re-evaluate historical events (bounded).
  - Topics: consume `events.normalized`, `synthetic.missed`; emit rule-evaluated/alerts in-process to aggregation/alerting.
- **Expectation Scheduler Service**
  - Responsibilities: durable timers for relative/absolute SLAs; scans `expectation` table by `due_at <= now`; emits synthetic missed events; ensures single firing via ShedLock/DB locks; polling cadence configurable via UI setting with target SLA alert latency ≤ 1 minute.
  - APIs: none external beyond health; config via Rule Engine writing expectations.
  - Topics: optionally consume `synthetic.missed` from external emitters; internal scheduler dispatches directly to rule engine.
- **Aggregation Service**
  - Responsibilities: consume rule evaluations to compute per-workflow/stage/group counts, backlog, aging; write to `stage_aggregate` table; expose for UI; maintain rolling windows.
  - APIs: `GET /workflows/{id}/aggregates?groupBy=...`; `GET /wallboard`.
  - Topics: none (in-process from rule engine).
- **Alerting/Notification Service**
  - Responsibilities: manage alert lifecycle (open/ack/suppress/resolved); dedupe; fan-out to channels (email at launch, pluggable for more); record audit.
  - APIs: `GET /alerts`, `POST /alerts/{id}/ack`, `POST /alerts/{id}/suppress`, `POST /alerts/{id}/resolve`.
  - Topics: none internally (in-process from rule engine); downstream channel adapters can be added separately.
- **UI Gateway/API Service**
  - Responsibilities: single REST facade for Angular; aggregates data from state/agg/alert services; enforces authz; caching.
  - APIs (REST/JSON): `/workflows`, `/workflows/{id}/graph`, `/workflows/{id}/tiles?group=...`, `/items/{key}`, `/alerts`, `/rules`, `/auth/me`.
- **Authn/Authz**
  - Use Keycloak/OIDC (or corporate IdP) with JWT; Spring Security resource servers in each service; role model: `viewer`, `operator` (ack/suppress), `config-admin`.

## Data Model (MariaDB)
- `event_raw` (partitioned by day)
  - `id` (PK, bigint), `source_event_id` (varchar, unique with `source_system`), `source_system`, `event_type`, `event_time_utc`, `received_at`, `workflow_key` (optional hint), `correlation_key`, `group_dims` (JSON), `payload` (JSON/JSONB), `ingest_status`.
  - Indexes: (`event_type`,`event_time_utc`), (`correlation_key`,`workflow_key`), (`received_at`).
  - Retention: configurable (default 30 days) hot table; archive older partitions into archival tables in MariaDB.
- `workflow` / `workflow_version`
  - `workflow(id PK, name, key, owner, active_version_id)`.
  - `workflow_version(id PK, workflow_id FK, version_num, definition_json, status, created_by, created_at, published_at)`.
  - `definition_json` stores nodes/edges + SLA rules; validated on write.
- `workflow_node`
  - `id PK`, `workflow_version_id FK`, `node_key` (unique per version), `event_type`, `is_start`, `is_terminal`, `ordering_policy` (strict/per-key).
- `workflow_edge`
  - `id PK`, `from_node_id FK`, `to_node_id FK`, `max_latency_sec`, `absolute_deadline` (time-of-day + tz), `optional` (bool), `severity`, `expected_count` (for batch counts).
- `workflow_run` (per item instance of workflow)
  - `id PK`, `workflow_version_id FK`, `correlation_key`, `group_dims` (JSON), `status` (green/amber/red), `started_at`, `updated_at`, `last_node_key`.
  - Index: (`workflow_version_id`,`correlation_key`), (`status`,`updated_at`).
- `event_occurrence`
  - `id PK`, `workflow_run_id FK`, `node_key`, `event_time_utc`, `received_at`, `payload_excerpt`, `is_late`, `is_duplicate`, `order_violation`, `raw_event_id FK`.
  - Index: (`workflow_run_id`,`node_key`), (`received_at`).
- `expectation`
  - `id PK`, `workflow_run_id FK`, `from_node_key`, `to_node_key`, `due_at`, `type` (relative/absolute), `status` (pending,fired,cleared), `severity`, `created_at`, `lock_owner` (for scheduler).
  - Index: (`due_at`,`status`), (`workflow_run_id`,`to_node_key`).
- `stage_aggregate` (partitioned daily)
  - `id PK`, `workflow_version_id`, `group_dim_hash`, `node_key`, `bucket_start` (minute), `in_flight`, `completed`, `late`, `failed`.
  - Index: (`workflow_version_id`,`group_dim_hash`,`bucket_start`).
- `alert`
  - `id PK`, `correlation_key`, `workflow_version_id`, `node_key`, `severity`, `state` (open,ack,suppressed,resolved), `dedupe_key`, `first_triggered_at`, `last_triggered_at`, `acked_by`, `acked_at`, `suppressed_until`.
  - Index: (`state`,`severity`), (`dedupe_key`), (`workflow_version_id`,`node_key`,`correlation_key`).
- `user`, `role`, `user_role`, `audit_log`
  - `audit_log` captures before/after for config changes and ack/suppress actions; index on (`entity_type`,`entity_id`,`created_at`).
- Partition/retention: raw/event_occurrence partition by day; aggregates by day or hour; alerts retained 180–365 days; workflow config non-expiring with soft-delete; archival jobs move old partitions to archival tables; durations configurable in system settings.

## Workflow/Rule Evaluation
- Graph model: adjacency list via `workflow_node` + `workflow_edge`; `definition_json` cached in Rule Engine; nodes keyed by business event types.
- Processing per event:
  - Determine workflow(s) via `workflow_key` hint or eventType/group mapping; allow fan-out to multiple workflows; enforce per-key ordering by Kafka partitioning on `correlation_key`.
  - Idempotency: dedupe using `source_event_id + source_system` and `raw_event_id`; duplicate marks `is_duplicate=true` and can be ignored or flagged per rule.
  - Ordering violations: compare expected next nodes; if out-of-order and no optional inbound edge allows it, flag `order_violation`, optionally create alert.
  - State transition: mark node complete, set timestamps, recompute workflow status (green/amber/red) from SLA flags.
  - Expectations: for each outgoing edge create `expected_count` pending rows (default 1) unless the edge is marked `optional`; `due_at = event_time + max_latency` or absolute deadline; cancel/clear one expectation per matching event; remaining expectations continue to track outstanding occurrences.
- Timer handling:
  - Expectation Scheduler polls `expectation` where `status=pending` and `due_at<=now`; locks row (`UPDATE ... WHERE status=pending LIMIT ...` with ShedLock-style owner) and emits synthetic missed event containing workflowRunId, edge info, severity.
  - Ensures exactly-once firing via `status` transitions and idempotent synthetic events (dedupe_key includes expectation id + due_at).
- SLA evaluation:
  - Relative SLA: check when target node arrival vs `due_at`; mark `is_late` and severity; stage and workflow statuses derive worst severity.
  - Absolute SLA: precompute day-specific deadline using tz; create expectation at ingest.
  - Batch counts: represent expected multiplicity via `expected_count` by creating multiple expectations; each event clears one, and remaining expectations can fire timers if missed.

## Observability & Command Center UI (Angular)
- Layout:
  - Wallboard mode: grid of workflows; each tile shows overall status, key group dimensions (e.g., region/book), in-flight/backlog, timers counting down; auto-refresh + dark/high-contrast theme.
  - Workflow view: left panel list of workflows, main canvas renders lifecycle graph with nodes colored green/amber/red, edges showing timer countdown or late badge; blink/pulse on red.
  - Stage tiles: per-node cards showing counts (in-flight, completed, late, failed), aging histogram, recent alerts, "time to SLA" countdown.
  - Alert strip: sticky header showing active alerts with severity color, time since breach, ack/suppress buttons.
  - Item drill-down: modal/page showing timeline of events for `correlation_key`, hop timestamps, durations, payload excerpt, related alerts.
  - Time display: backend in UTC; UI displays in system timezone with toggle to UTC for triage consistency.
- Component structure (Angular):
  - `app.module` -> routes: `/wallboard`, `/workflow/:id`, `/item/:key`, `/alerts`, `/rules`.
  - Shared components: `GraphCanvasComponent` (renders nodes/edges via D3/Canvas), `StageTileComponent`, `CountdownBadge`, `AlertStrip`, `LifecycleTimeline`, `GroupSelector`.
  - Services: `ApiClient` (typed via OpenAPI), `WebsocketService` (primary for wallboard/push updates with polling fallback), `ThemeService` (wallboard), `AuthService` (OIDC), `StateStore` (NgRx or Akita).
  - Accessibility: keyboard navigation, high-contrast palettes, blink replaced with pulse for WCAG.

## Alerting & Notification
- Lifecycle: `open` (first trigger) → `ack` (human) → `suppressed` (manual) → `resolved` (condition clears) → `closed` (optional archive). Auto-resolve when rule condition clears and no new triggers within cool-down.
- Dedupe: `dedupe_key = workflowVersionId + nodeKey + correlationKey + ruleCode`; new triggers update `last_triggered_at` and increment count instead of new rows.
- Suppressions: manual suppress with reason + expiry; suppressed alerts still logged but no notifications.
- Channels: email (SMTP) at launch; pluggable adapter interface to add webhook/Teams/PagerDuty later.

## Security & Ops
- Authn/authz: OIDC (Keycloak or corporate IdP); JWT validation in each service; roles enforced at API; fine-grained permissions for config changes and acknowledgements.
- Tenancy: single-tenant deployment initially; no tenant_id columns; revisit if multi-tenant required.
- Transport security: TLS everywhere; Kafka with TLS/SASL; secrets via vault (e.g., HashiCorp Vault or Kubernetes secrets) injected as env vars; no secrets in images.
- Config management: externalized config via Spring Cloud Config or environment; feature flags for experimental UI and replay; database migrations via Flyway.
- Observability: Spring Boot Actuator health/metrics; Prometheus scraping; structured JSON logs with correlation IDs; tracing via OpenTelemetry (Kafka + REST propagation).
- Backup/restore: MariaDB backups (binlog + nightly); partition-level purges via scheduled jobs; DR runbooks.
- Operations: rate limits/throttling on ingest; DLQ monitoring dashboards; replay tooling for historical re-evaluation.

## Scaling & Reliability
- Throughput: partition Kafka by `correlation_key` to preserve per-key order (sufficient; no global ordering needed); scale consumers horizontally; use bounded thread pools.
- Idempotency: `source_event_id` unique constraint; consumer processing idempotent (upserts on `workflow_run` and `expectation`); synthetic events deduped.
- Retries/backoff: Kafka consumer retries with DLQ on poison; HTTP ingest returns 429 on overload; notification retries with exponential backoff and circuit breakers (Resilience4j).
- Timers durability: `expectation` table + DB-backed scheduler with ShedLock; avoid in-memory timers; ensure time sync via NTP; poll interval configurable (UI-driven), tuned to keep SLA alert latency ≤ 1 minute.
- Schema evolution: Flyway migrations; add columns with defaults; versioned workflow definitions; topic schema evolution via schema registry (Avro/JSON Schema).
- Volume spikes: ingest buffering in Kafka; back-pressure via consumer lag; aggregates computed incrementally to avoid heavy scans; optional sampling for UI when high volumes.
- Clock skew/timezone: normalize to UTC; store source event time and received time; UI displays in system timezone; SLA based on received if skew exceeds threshold.

## Decisions (closed)
- Messaging: Kafka confirmed; REST ingest supported for Kafka-less sources; internal flow continues via Kafka.
- Retention: hot table retention configurable (default 30 days) with archival tables for raw/occurrence; aggregates follow configurable policy.
- Ordering guarantees: per-correlation-key ordering via Kafka partitions only; no global ordering required.
- Notification channels: email at launch; adapters exist to add webhook/Teams/PagerDuty later.
- UI push model: WebSocket preferred for wallboard/live updates; polling/ETag as fallback.
- Timer scan cadence: scheduler interval configurable via UI; target SLA alert latency ≤ 1 minute.
- Multi-tenancy: single-tenant for now; `tenant_id` can be added later if needed.
- Timezone handling: backend stores UTC and computes absolute SLAs tz-aware; UI displays in system timezone with DST awareness.

## Developer-Facing Technical Design
- REST (UI Gateway/API) — sample endpoints/payloads:
  - `GET /workflows`: returns list with `id`, `name`, `status`, `activeVersion`.
  - `GET /workflows/{id}/graph`: returns nodes/edges + SLAs.
  - `GET /workflows/{id}/tiles?group=region:NY`: returns per-stage counts/backlog and timers.
  - `GET /items/{correlationKey}`:
    ```json
    {
      "workflowId": "trade-lifecycle",
      "correlationKey": "TR123",
      "group": {"book":"EQD","region":"NY"},
      "events": [
        {"node":"ingest","eventTime":"2024-05-09T12:01Z","receivedAt":"2024-05-09T12:01:02Z","late":false},
        {"node":"sys2-verify","eventTime":"2024-05-09T12:02Z","late":false}
      ],
      "status": "amber",
      "pendingExpectations": [
        {"from":"sys2-verify","to":"sys3-send","dueAt":"2024-05-09T12:07Z","remainingSec":180}
      ]
    }
    ```
  - `POST /alerts/{id}/ack` body: `{"reason":"Investigating","ticket":"INC123"}`.
  - `POST /workflows` payload:
    ```json
    {
      "name": "Trade Lifecycle",
      "key": "trade-lifecycle",
      "graph": {
        "nodes": [
          {"key":"ingest","eventType":"TRADE_INGEST","start":true},
          {"key":"sys2-verify","eventType":"SYS2_VERIFIED"},
          {"key":"sys3-ack","eventType":"SYS3_ACK","terminal":true}
        ],
        "edges": [
          {"from":"ingest","to":"sys2-verify","maxLatencySec":300,"severity":"red","expectedCount":2},
          {"from":"sys2-verify","to":"sys3-ack","absoluteDeadline":"08:00Z","severity":"amber","optional":true}
        ]
      },
      "groupDimensions":["book","region"]
    }
    ```
- Kafka topic schemas (JSON):
  - `events.normalized`:
    ```json
    {
      "eventId":"uuid",
      "sourceSystem":"sys1",
      "eventType":"TRADE_INGEST",
      "eventTime":"2024-05-09T12:01:00Z",
      "receivedAt":"2024-05-09T12:01:02Z",
      "workflowKey":"trade-lifecycle",   // optional hint when producer knows target workflow
      "workflowKeys":["trade-lifecycle","trade-reporting"], // optional fan-out hint
      "correlationKey":"TR123",
      "group": {"book":"EQD","region":"NY"},
      "payload": {...}
    }
    ```
  - `synthetic.missed`: `{"expectationId":123,"workflowRunId":456,"fromNode":"sys2-verify","toNode":"sys3-ack","dueAt":"...","severity":"red","dedupeKey":"exp-123-20240509"}`
  - `rule.evaluated`: `{"workflowRunId":456,"workflowVersionId":12,"node":"sys3-ack","status":"green","late":false,"orderViolation":false,"inFlightDeltas":{"sys3-ack":1},"completedDelta":1,"lateDelta":0,"failedDelta":0,"groupHash":"abc123","eventTime":"...","receivedAt":"..." }`
  - `alerts.triggered`: `{"dedupeKey":"...","workflowRunId":456,"node":"sys3-ack","correlationKey":"TR123","severity":"red","reason":"SLA_MISSED","triggeredAt":"..."}` 
- DDL outline (Flyway-friendly):
  ```sql
  CREATE TABLE event_raw (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_event_id VARCHAR(200) NOT NULL,
    source_system VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    workflow_key VARCHAR(100),
    correlation_key VARCHAR(200) NOT NULL,
    group_dims JSON,
    event_time_utc DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL,
    payload JSON,
    ingest_status VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_event_source (source_system, source_event_id),
    KEY idx_event_time (event_type, event_time_utc),
    KEY idx_corr (workflow_key, correlation_key)
  ) PARTITION BY RANGE (TO_DAYS(received_at));

  CREATE TABLE expectation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_run_id BIGINT NOT NULL,
    from_node_key VARCHAR(100) NOT NULL,
    to_node_key VARCHAR(100) NOT NULL,
    due_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    lock_owner VARCHAR(100),
    created_at DATETIME(3) NOT NULL,
    KEY idx_due_status (status, due_at),
    KEY idx_run_to (workflow_run_id, to_node_key)
  );
  ```
- Angular build plan:
  - Generate OpenAPI client from UI Gateway spec (ng-openapi-gen).
  - Route components: `WallboardPage`, `WorkflowPage`, `ItemPage`, `AlertsPage`, `RulesPage`.
  - State: NgRx slices `workflows`, `alerts`, `items`, `wallboard`; effects fetching via `ApiClient`.
  - Graph rendering: D3 with curved edges, node color by status; tooltips for timers; responsive layout for wallboard.
  - Theming: CSS variables for green/amber/red; wallboard uses large typography; countdown animations via CSS transitions.
- Spring implementation notes:
  - Use Spring Cloud Stream Kafka binder; consumers annotated with `@StreamListener`/`@KafkaListener` (functional style preferred).
  - MapStruct for DTO ↔ entity; Jackson for JSON; validation via Spring Validation + JSON Schema.
  - Scheduler uses Quartz + ShedLock for distributed locks; batch `SELECT ... FOR UPDATE SKIP LOCKED` on expectations.
  - Resilience: Resilience4j circuit breakers on outbound notification calls; retry templates with backoff.

## Rationale
- Decoupled ingest/rule/alert paths reduce coupling to producers and allow independent scaling.
- DB-backed expectations keep timers durable and auditable; Kafka ensures resilient event transport.
- Aggregates precomputed to keep UI queries fast; raw events retained separately for replay/audit.
- Keycloak/OIDC aligns with enterprise SSO; Spring stack chosen for operational familiarity.
