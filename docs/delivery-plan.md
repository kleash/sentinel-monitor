# Event-Based Monitoring System — Delivery Plan

## Assumptions & Dependencies
- Kafka available with per-key ordering only (partition on `correlation_key`); topics: `events.raw`, `events.normalized`, `synthetic.missed`, `rule.evaluated`, `alerts.triggered`, DLQs; schema registry (JSON/Avro) enabled.
- MariaDB with Flyway; 30-day hot retention on partitioned tables with archival tables/jobs; backups/binlog + NTP time sync.
- Keycloak/OIDC for auth; roles `viewer`, `operator`, `config-admin`; TLS on REST/Kafka; secrets from vault.
- CI/CD with container registry; Kubernetes/VM runtime; SMTP for email notifications (launch channel); observability stack (Prometheus/Grafana, log agg, OTel collector); feature flag service.
- Single-tenant deployment; storage in UTC; UI displays local time with UTC toggle; WebSocket push supported by infra.

## Milestones & Phases (entry/exit, parallelization)
1) **Foundation & Environments** — Entry: core infra (Kafka, MariaDB, Keycloak, SMTP, CI/CD) reachable. Exit: Spring Boot/Angular skeletons with shared logging/auth libs, Flyway baseline, schema registry subjects, health/metrics wired.
2) **Ingestion Path** — Entry: foundation done. Exit: Kafka + REST ingest writing `event_raw`, emitting `events.normalized`, DLQ path, idempotency guard, throughput/error metrics.
3) **Rule Config & Workflow Management** — Entry: normalized events available. Exit: workflow CRUD/version/publish with validation + dry-run, config events emitted, secured APIs/UI forms.
4) **Rule Engine Core** — Entry: published workflows in store. Exit: event consumption + workflow resolution/fan-out, state persisted (`workflow_run`, `event_occurrence`, `expectation`), `rule.evaluated` emitted, replay endpoint bounded.
5) **Expectation Scheduler (Timers)** — Entry: expectations written. Exit: locked polling with ShedLock, `synthetic.missed` emitted within ≤1 minute target, lag metrics/alerts.
6) **Aggregation & Read Models** — Entry: rule evaluations flowing. Exit: `stage_aggregate` maintained, wallboard/aggregate APIs live, cached in UI Gateway.
7) **Alerting & Notifications** — Entry: evaluation outcomes available. Exit: alert lifecycle persisted/deduped, email adapter live, suppression/maintenance windows, DLQ + metrics.
8) **UI Gateway & Angular UI** — Entry: read/query endpoints stable. Exit: wallboard/workflow/item/alerts/rules pages with WebSocket push + polling fallback, auth flows, accessibility/responsiveness.
9) **Hardening & Ops Readiness** — Entry: end-to-end happy path proven. Exit: perf/scale/chaos/replay drills, runbooks, retention/archival jobs scheduled, go-live checklist.

Parallelization: after phase 1, phases 2 and 3 can proceed in parallel; phase 6 can start once `rule.evaluated` schema is stable; UI (phase 8) can start with mocks alongside phases 4–7; alerting email adapter can be stubbed while SMTP access completes.

## Epics & Stories (per phase with acceptance)
- **Foundation (1)**
  - Establish shared BOM/starters (OIDC, Actuator, logging/tracing) and service templates; Acceptance: new service builds green, secured health endpoints.
  - CI/CD pipelines: unit/contract/integration (testcontainers), Docker build/push, Flyway migrate; Acceptance: pipeline gates block schema breaking changes.
  - Bootstrap schema registry subjects for all topics; Acceptance: compatibility checks enforced in CI.
- **Ingestion (2)** — blocked on foundation only.
  - Kafka consumer on `events.raw` + REST `/ingest` with idempotency-key; write `event_raw`; emit `events.normalized`; DLQ bad events. Acceptance: duplicate submissions do not double-write; DLQ receives invalid payloads; metrics for throughput/error.
  - Validation/normalization (JSON schema/Avro, UTC conversion, metadata enrichment). Acceptance: non-conforming payloads rejected with reason; timestamps stored UTC.
  - Resilience (retry/backoff, rate limit/429); Acceptance: overload returns 429 without dropping Kafka flow.
- **Rule Config (3)** — parallel with ingestion.
  - CRUD/version/publish workflows; immutable published versions; dry-run `/simulate`. Acceptance: invalid graphs rejected; publish sets active version only via workflow owner role.
  - Emit `config.workflow.updated` for cache invalidation; Acceptance: Rule Engine cache refresh observed in logs/metrics.
  - UI forms for create/edit/publish with preview graph. Acceptance: form validation prevents save on invalid edges.
- **Rule Engine (4)** — blocked on published workflows + normalized events.
  - Consumers for `events.normalized` + `synthetic.missed`; workflow resolution via hint + eventType/group mapping; fan-out support. Acceptance: per-key ordering preserved; fan-out emits multiple state updates.
  - State transitions, dup/order detection, expectation creation/clear, emit `rule.evaluated` + `alerts.triggered`. Acceptance: late/order violation flags stored; expectations have correct `due_at`.
  - `/replay` endpoint bounded by time/range with replay flag to suppress duplicate alerts. Acceptance: replay produces deterministic state without new alerts when flag set.
- **Expectation Scheduler (5)** — blocked on expectations table population.
  - Poll `expectation` with `FOR UPDATE SKIP LOCKED` + ShedLock owner; emit `synthetic.missed` with dedupe key. Acceptance: no double-fire in concurrent workers; fired status updated.
  - Configurable interval (UI-driven); lag/scan metrics. Acceptance: SLA miss emitted within target ≤1 minute.
- **Aggregation (6)** — blocked on `rule.evaluated`.
  - Consume `rule.evaluated` to upsert `stage_aggregate` partitions; rolling window queries. Acceptance: aggregates reflect new events within seconds; partition rollover proven.
  - APIs `/workflows/{id}/aggregates`, `/wallboard`. Acceptance: contract tests green; caches invalidate on config update.
- **Alerting (7)** — blocked on `alerts.triggered`.
  - Alert lifecycle (open/ack/suppress/resolved), dedupe on key, audit log. Acceptance: repeated triggers update `last_triggered_at`; audit captures actor/reason.
  - Suppression/maintenance windows; email adapter with templates; DLQ on failures. Acceptance: suppressed alerts skip email; DLQ populates on channel errors.
- **UI Gateway & Angular UI (8)** — depends on read APIs; can mock early.
  - UI Gateway endpoints: workflows list/graph/tiles, items timeline, alerts console, rules CRUD proxy, `/auth/me`; caching + authz. Acceptance: unauthorized requests rejected; cached responses refreshed on config events.
  - WebSocket push for wallboard/alerts with polling fallback. Acceptance: disconnect forces polling without data loss.
  - Angular pages `/wallboard`, `/workflow/:id`, `/item/:key`, `/alerts`, `/rules`; D3 graph, countdown badges, NgRx stores, OpenAPI client. Acceptance: live statuses render; ack/suppress flows work; accessibility checks pass.
- **Hardening (9)**
  - Load/perf (Kafka ingest, Rule Engine throughput, scheduler latency), soak tests; Acceptance: target volume (per SLO) sustained with acceptable lag.
  - Chaos/DLQ/replay drills; retention/archival jobs; backups/restore; runbooks. Acceptance: DLQ drain runbook validated; archival moves partitions after 30 days; restore test passes.

## Service Work Breakdown
- **Ingestion Service**
  - Build Kafka consumer (`events.raw`) and REST `/ingest`; normalize to UTC; enforce idempotency (`source_event_id` + `source_system`); write `event_raw`; produce `events.normalized`; DLQ bad events.
  - Add schema validation, rate limits, retries/backoff; health + lag metrics; structured logs with correlation key.
  - Tests: REST validation + idempotency, Kafka contract (producer/consumer), DB integration (testcontainers).
- **Rule Config Service**
  - APIs: `POST/PUT/GET /workflows`, versions list, `:publish`, `/simulate`; graph validation; version immutability; emit `config.workflow.updated`.
  - Audit logging for changes; cache layer; role checks (`config-admin`).
  - Tests: validation units, API contracts, event emission.
- **Rule Engine Service**
  - Consumers for `events.normalized` and `synthetic.missed`; workflow resolution; state machine; persistence to `workflow_run`, `event_occurrence`, `expectation`; emit `rule.evaluated` + `alerts.triggered`.
  - `/items/{key}` timeline, `/workflows/{id}/state`, `/replay` bounded; idempotent handlers; DLQ on poison.
  - Tests: ordering/dup edge cases, expectation creation, replay determinism, topic contracts, DB integration.
- **Expectation Scheduler**
  - Poll `expectation` with locks; emit `synthetic.missed`; configurable poll interval; health/lag endpoint; ShedLock setup.
  - Tests: concurrency/lock behavior, dedupe key correctness, latency measurement.
- **Aggregation Service**
  - Consume `rule.evaluated`; upsert `stage_aggregate` (partitioned); APIs `/workflows/{id}/aggregates`, `/wallboard`; caching.
  - Tests: aggregation correctness, partition rollover, API contracts.
- **Alerting/Notification Service**
  - Consume `alerts.triggered`; manage `alert` table; dedupe/suppress/ack/resolved; maintenance windows; email adapter; audit.
  - DLQ and retry/backoff with circuit breaker; metrics by state/severity.
  - Tests: dedupe logic, suppression, email mock, DLQ path.
- **UI Gateway/API**
  - Facade endpoints: workflows/graph/tiles/items/alerts/rules/auth/me; caching; authz enforcement; rate limiting; WebSocket endpoint for push.
  - Tests: contract tests, authz, cache invalidation.
- **Angular UI**
  - Generate OpenAPI client; implement pages/components (GraphCanvas, StageTile, AlertStrip, CountdownBadge, LifecycleTimeline); WebSocket client with polling fallback; timezone toggle; wallboard mode.
  - Tests: component/unit, Cypress e2e for wallboard/workflow/item/alerts, WebSocket disconnect scenarios.

## Data & Schema Tasks
- Flyway migrations for all tables in architecture: `event_raw`, `workflow`/`workflow_version`/`workflow_node`/`workflow_edge`, `workflow_run`, `event_occurrence`, `expectation`, `stage_aggregate`, `alert`, `user`/`role`/`user_role`, `audit_log`; partition `event_raw` and `stage_aggregate`; create archival tables and partition move jobs.
- Indexes per design (event type/time, correlation/workflow, expectation due/status, aggregates by workflow/group/bucket, alerts state/severity, audit by entity/time).
- Seed non-prod data: sample workflows/rules, roles/users, runbook samples.
- Schema registry artifacts for all Kafka topics; CI compatibility checks; evolution guidelines documented.
- Data backfill/replay scripts to load historical events into `event_raw` and invoke `/replay` with replay flag to avoid duplicate alerts.

## Cross-Cutting
- Security: OIDC/JWT in every service; role-based authz; TLS for REST/Kafka; secrets from vault; audit for config and alert actions.
- Observability: Actuator health/readiness; Prometheus metrics (ingest throughput, rule latency, scheduler lag, alert rates); JSON logs with correlation IDs; OpenTelemetry tracing across REST/Kafka.
- Config management: externalized configs per env; feature flags (replay, WebSocket fallback, experimental UI); environment-specific Kafka/MariaDB endpoints.
- Reliability: idempotency across consumers/producers; retries/backoff; DLQ handling with drain tooling; bounded thread pools; backpressure and rate limits on ingest.
- CI/CD: pipelines run unit/contract/integration (testcontainers), lint/format, Docker build, deploy to dev/qa/prod; Flyway migration gate; schema registry compatibility gate.
- Performance/scale: load tests for ingest/eval; scheduler latency benchmarks; Kafka lag dashboards; DB pool tuning; capacity planning for Kafka partitions per workflow load.

## Test Strategy
- Unit tests: graph validation, workflow resolution, aggregation math, alert dedupe/suppression, UI component logic.
- Contract tests: REST APIs (UI Gateway, Rule Config, Alerting) and Kafka schemas for producers/consumers.
- Integration tests: ingest → rule eval → alert path with MariaDB + Kafka testcontainers; scheduler firing; replay path with suppression of duplicate alerts.
- E2E tests: wallboard/live updates, workflow graph rendering, item timeline, ack/suppress flows, missing-event timers; WebSocket drop → polling fallback.
- Replay/backfill validation: scripted historical load to `event_raw`, run `/replay`, verify state/alerts deterministic.

## Release & Ops Readiness
- Runbooks: deploy/rollback, DLQ drain, replay/backfill, scheduler lag recovery, Kafka consumer lag triage, archival/retention adjustments, SMTP failure handling.
- Feature flags: replay mode, WebSocket push toggle, UI experiments.
- Health/readiness per service; capacity configs (Kafka partitions, consumer concurrency, DB pool sizes); retention/archival schedules verified.
- Dashboards/alerts: ingest throughput, rule latency, scheduler lag, alert rates/state changes, WebSocket connections, DLQ depth.
- DR: backup/restore tested; clock sync monitoring; SMTP credential rotation.

## Open Risks/Decisions
- Confirm throughput and peak rates per workflow to size Kafka partitions and DB resources.
- Clarify acceptable alert latency and scheduler interval bounds for ops (default target ≤1 minute).
- Define schema evolution governance/approvals for topics and DB; ownership for registry subjects.
- Nail replay semantics for alert suppression vs. audit needs; how to flag replayed events in UI.
- UI performance for large graphs—may need progressive render/virtualization; set supported node/edge limits.
