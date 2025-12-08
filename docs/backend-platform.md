# Backend Platform Guide

## 1. Overview
- Event-driven Spring Boot service that ingests raw events, applies workflow-based rules, manages expectations/timers, aggregates status, and tracks alert lifecycles.
- Architecture style: layered with REST + Kafka adapters on the edges, service layer orchestrating logic, and JDBC/JPA repositories against MariaDB; messaging-first between stages.
- Key technologies: Spring Boot 3, Spring Cloud Stream + Spring Kafka, MariaDB (Flyway migrations), Spring Data JPA + JdbcTemplate, OAuth2 JWT resource server, Micrometer/Actuator.

## 2. System Architecture

### 2.1 High-Level Architecture
- Inbound adapters: REST controllers and Kafka listeners convert external input into internal models.
- Services encapsulate normalization, workflow evaluation, scheduling, aggregation, and alert handling.
- Persistence adapters: JPA for workflow config; JdbcTemplate for runtime state, expectations, aggregates, and alerts.
- Messaging: Kafka topics connect ingest → rule engine → aggregation/alerting; scheduler emits synthetic events back into the engine.
- Security: OAuth2 resource server with role checks; health endpoint open, others secured.

### 2.2 Main Modules / Packages
| Package/Module | Responsibility | Key Features Implemented | Important Dependencies |
| --- | --- | --- | --- |
| `com.sentinel.platform.ingestion` | Accept raw events (Kafka or REST), validate/normalize, persist for idempotency, publish normalized or DLQ events | `/ingest`, `rawEventsConsumer`, `events.normalized`/`events.dlq` publishing | Spring Cloud Stream `StreamBridge`, `JdbcTemplate`, Micrometer, `ingestion` properties |
| `com.sentinel.platform.ruleconfig` | Workflow definition storage and activation | `/workflows` list/get/create; graph persistence into workflow tables | Spring Data JPA, `ObjectMapper` |
| `com.sentinel.platform.ruleengine` | Rule evaluation, runtime state, expectation management, scheduler, read-model timeline | Kafka listeners on normalized and synthetic topics; expectation polling; `/items/{correlationKey}` | `KafkaTemplate`, `JdbcTemplate`, `Clock`, `RuleEngineProperties` |
| `com.sentinel.platform.aggregation` | Maintain per-stage aggregates for dashboards | Kafka consumer on `rule.evaluated`; `/workflows/{id}/aggregates`, `/wallboard` | `JdbcTemplate`, `ObjectMapper` |
| `com.sentinel.platform.alerting` | Alert upsert from rule outcomes and lifecycle actions with audit | Kafka consumer on `alerts.triggered`; `/alerts` list + ack/suppress/resolve | `JdbcTemplate`, `ObjectMapper`, `Clock` |
| `com.sentinel.platform.config` | Cross-cutting config (security, time) | OAuth2 resource server, UTC clock bean | Spring Security |

## 3. Feature & Flow Guide

### 3.1 Ingestion (REST + Kafka)
- Business description: accept producer events, guard against malformed input, persist for dedupe, and publish normalized envelopes.
- Entry points: `IngestController` REST (`backend/platform-service/src/main/java/com/sentinel/platform/ingestion/web/IngestController.java`); Kafka `rawEventsConsumer` (`ingestion/stream/StreamHandlers.java`).
- Main packages: `ingestion.*`.
- High-level call flow: REST POST `/ingest` → `IngestRateLimiter` → `IngestionService.normalize`/`persistAndPublish` → `EventRawRepository` (idempotent insert) → `NormalizedEventPublisher` (`events.normalized`) or `DlqPublisher`.
- Key database tables: `event_raw` (raw ingest with unique `(source_system, source_event_id)`).

### 3.2 Workflow Configuration
- Business description: store workflow graphs and expose active versions.
- Entry points: `/workflows` GET/POST via `WorkflowController`.
- Main packages: `ruleconfig.*`.
- High-level call flow: controller → `WorkflowService.createWorkflow` → JPA repositories persist `workflow`, `workflow_version`, `workflow_node`, `workflow_edge` (including `maxLatencySec`, `absoluteDeadline`, `expectedCount`, `optional`, `severity`); active version set on create.
- Key database tables: `workflow`, `workflow_version`, `workflow_node`, `workflow_edge`.

### 3.3 Rule Evaluation
- Business description: resolve applicable workflow versions for each normalized event, manage workflow runs, expectations, and emit evaluation + alerts.
- Entry points: Kafka listener on `${ruleengine.normalized-topic}` in `RuleEngineListeners`.
- Main packages: `ruleengine.*` (excluding scheduler).
- High-level call flow: listener → `RuleEngineService.handleNormalizedEvent` → `RuleEngineStateRepository` to load/create run; clear expectations one-at-a-time; create expectations per outgoing edge (skipping optional edges, repeating per `expectedCount`, honoring `absoluteDeadline`); suppress order violations when only optional inbound edges exist → `RuleEventPublisher.publishRuleEvaluated` (`rule.evaluated`) and `publishAlertTriggered` when late/order issues → DB updates to `workflow_run`, `event_occurrence`, `expectation`.
- Key database tables: `workflow_run`, `event_occurrence`, `expectation`.

### 3.4 Expectation Scheduler
- Business description: poll due expectations and emit synthetic misses to close loops on timers.
- Entry points: `ExpectationSchedulerService.scheduledPoll` (configurable fixed delay).
- Main packages: `ruleengine.service` + `ruleengine.repository.ExpectationRepository`.
- High-level call flow: scheduled poll → `ExpectationRepository.claimDuePending` (marks fired) → `ExpectationSchedulerService.pollAndEmit` serializes `SyntheticMissedEvent` → Kafka `${ruleengine.synthetic-topic}` → consumed by `RuleEngineService.handleSyntheticMissed` to update run and emit alert/evaluation.
- Key database tables: `expectation`.

### 3.5 Aggregation & Wallboard
- Business description: maintain per-node counts for wallboards and per-workflow aggregates.
- Entry points: Kafka listener on `${ruleengine.rule-evaluated-topic}`; REST `/workflows/{id}/aggregates`, `/wallboard`.
- Main packages: `aggregation.*`.
- High-level call flow: listener → `AggregationService.handleRuleEvaluated` → `StageAggregateRepository.upsert` per minute bucket (adjust in-flight/completed/late/failed) → REST queries the same table for views.
- Key database tables: `stage_aggregate`.

### 3.6 Alert Lifecycle
- Business description: dedupe/update alerts from rule outcomes and allow operators to ack/suppress/resolve with audit.
- Entry points: Kafka listener on `${ruleengine.alerts-triggered-topic}`; REST `/alerts`, `/alerts/{id}/ack|suppress|resolve`.
- Main packages: `alerting.*`.
- High-level call flow: listener → `AlertingService.handleAlertTriggered` → `AlertRepository.upsert`; lifecycle endpoints → `AlertingService` ack/suppress/resolve → `AlertRepository.updateState` + `AuditRepository.record`.
- Key database tables: `alert`, `audit_log`.

### 3.7 Item Timeline & Read Models
- Business description: expose latest run timeline, expectations, alerts, and aggregates for UI.
- Entry points: `/items/{correlationKey}` (`ruleengine/web/ItemController.java`), `/workflows/{id}/aggregates`, `/wallboard`, `/alerts`.
- Main packages: `ruleengine.web`, `aggregation.web`, `alerting.web`.
- High-level call flow: controllers query `workflow_run`, `event_occurrence`, `expectation`, `alert`, and `stage_aggregate` directly via `JdbcTemplate` and return assembled maps/lists.
- Key database tables: `workflow_run`, `event_occurrence`, `expectation`, `alert`, `stage_aggregate`.

## 4. Package Reference

## 4.1 Package: com.sentinel.platform.ingestion

### 4.1.1 Responsibility
- Handles REST/Kafka ingest, validation, normalization, dedupe persistence, and publishing normalized or DLQ events. Part of inbound/API layer with messaging integration.

### 4.1.2 Key Classes
| Class Name | Type | Responsibility | Related Tables |
| --- | --- | --- | --- |
| `ingestion/web/IngestController` | Controller | REST `/ingest`, applies idempotency header, delegates through rate limiter | `event_raw` |
| `ingestion/stream/StreamHandlers` | Kafka Consumer | `rawEventsConsumer` for `events.raw`, forwards to ingestion service | `event_raw` |
| `ingestion/service/IngestionService` | Service | Validate/normalize `RawEventRequest`, persist, publish normalized/DLQ, dedupe by `eventId` | `event_raw` |
| `ingestion/service/IngestRateLimiter` | Component | Semaphore-based concurrency guard for REST ingest | - |
| `ingestion/service/RawEventValidator` | Component | Envelope validation for required fields and timestamp parsing | - |
| `ingestion/repository/EventRawRepository` | Repository (JdbcTemplate) | Insert raw events with unique constraint handling | `event_raw` |
| `ingestion/service/NormalizedEventPublisher` | Publisher | Sends normalized events via Cloud Stream binding `normalizedEvents-out-0` | - |
| `ingestion/service/DlqPublisher` | Publisher | Sends invalid/failed payloads to `dlq-out-0` | - |

### 4.1.3 Typical Class Flow
- REST: `IngestController` → `IngestRateLimiter.execute` → `IngestionService.ingestFromRest` → `RawEventValidator` → `EventRawRepository.save` → `NormalizedEventPublisher.publish`.
- Kafka: `StreamHandlers.rawEventsConsumer` → `IngestionService.ingestFromKafka` (DLQ on validation/processing errors).

### 4.1.4 Database Tables
- `event_raw`: stores normalized envelopes for idempotency and auditing; unique on `(source_system, source_event_id)`.

### 4.1.5 Example Usage
- POST `/ingest` with `{eventType, eventTime, correlationKey, payload}` → normalized in `IngestionService.normalize` → inserted into `event_raw` → published to `events.normalized` with key `correlationKey`.

## 4.2 Package: com.sentinel.platform.ruleconfig

### 4.2.1 Responsibility
- Workflow definition management (CRUD + activation). Domain/config layer using JPA.

### 4.2.2 Key Classes
| Class Name | Type | Responsibility | Related Tables |
| --- | --- | --- | --- |
| `ruleconfig/web/WorkflowController` | Controller | `/workflows` list/get/create endpoints | `workflow`, `workflow_version`, `workflow_node`, `workflow_edge` |
| `ruleconfig/service/WorkflowService` | Service | Enforce unique keys, create initial version, persist graph nodes/edges, set active version | same |
| `ruleconfig/repository/*Repository` | Repository (JPA) | CRUD and queries for workflows, versions, nodes, edges | same |
| `ruleconfig/model/*` | Entities | JPA mappings for workflow tables | same |

### 4.2.3 Typical Class Flow
- `WorkflowController.create` → `WorkflowService.createWorkflow` → persist `Workflow` and `WorkflowVersion` → `persistGraph` saves `WorkflowNode` and `WorkflowEdge` → set `active_version_id` on workflow.

### 4.2.4 Database Tables
- `workflow`: workflow metadata + active version pointer.
- `workflow_version`: versioned definitions with JSON graph.
- `workflow_node`: per-version nodes keyed by event type.
- `workflow_edge`: edges with latency/severity metadata including `max_latency_sec`, `absolute_deadline`, `expected_count`, `optional`, `severity`.

### 4.2.5 Example Usage
- POST `/workflows` → `WorkflowService` writes new workflow/version, nodes/edges, marks active; subsequent GET `/workflows/{key}` reads via `WorkflowRepository.findByKey`.

## 4.3 Package: com.sentinel.platform.ruleengine

### 4.3.1 Responsibility
- Apply workflows to normalized events, manage workflow runs and expectations, publish evaluation and alerts, expose item timeline. Core domain/service layer plus messaging adapters.

### 4.3.2 Key Classes
| Class Name | Type | Responsibility | Related Tables |
| --- | --- | --- | --- |
| `ruleengine/kafka/RuleEngineListeners` | Kafka Consumer | Consume normalized and synthetic topics, delegate to service | `workflow_run`, `event_occurrence`, `expectation` |
| `ruleengine/service/RuleEngineService` | Service | Resolve target workflow versions, dedupe events, clear/create expectations, compute status, publish evaluation/alerts | same |
| `ruleengine/repository/RuleEngineStateRepository` | Repository (JdbcTemplate) | Manage runs, occurrences, expectations, and node lookup | same |
| `ruleengine/repository/ExpectationRepository` | Repository (JdbcTemplate) | Claim due expectations and mark fired | `expectation` |
| `ruleengine/service/ExpectationSchedulerService` | Scheduler | Poll due expectations and emit `SyntheticMissedEvent` to Kafka | `expectation` |
| `ruleengine/service/RuleEventPublisher` | Publisher | Send `rule.evaluated` and `alerts.triggered` events via KafkaTemplate | - |
| `ruleengine/web/ItemController` | Controller | `/items/{correlationKey}` timeline composed from runtime tables | `workflow_run`, `event_occurrence`, `expectation`, `alert` |

### 4.3.3 Typical Class Flow
- Normalized event: `RuleEngineListeners.onNormalized` → `RuleEngineService.handleNormalizedEvent` → `RuleEngineStateRepository.findRunId`/`createRun` → clear expectations → create new expectations for outgoing edges → `saveOccurrence` → `RuleEventPublisher.publishRuleEvaluated`; if late/order violation → `RuleEventPublisher.publishAlertTriggered`.
- Synthetic miss: `RuleEngineListeners.onSyntheticMissed` → `RuleEngineService.handleSyntheticMissed` → load run context → publish `RuleEvaluatedEvent` (late) + alert.
- Scheduler: `ExpectationSchedulerService.scheduledPoll` → `ExpectationRepository.claimDuePending` → publish `SyntheticMissedEvent` via KafkaTemplate.

### 4.3.4 Database Tables
- `workflow_run`: runtime workflow instance per correlation key/version.
- `event_occurrence`: events applied to runs with late/dup/order flags.
- `expectation`: pending/fired/cleared expectations with due time, severity, lock owner, fired_at.

### 4.3.5 Example Usage
- Normalized message on `events.normalized` with `eventType=PAYMENT_INIT` → `RuleEngineService` resolves workflow version via `workflow_key` or active nodes → creates run + expectations → stores occurrence → publishes `rule.evaluated` (in-flight deltas) and, if late/order violation, `alerts.triggered` with dedupe key `${versionId}:${node}:${correlationKey}`.

## 4.4 Package: com.sentinel.platform.aggregation

### 4.4.1 Responsibility
- Maintain per-stage aggregates for dashboards by consuming rule evaluation events. Infra/reporting layer.

### 4.4.2 Key Classes
| Class Name | Type | Responsibility | Related Tables |
| --- | --- | --- | --- |
| `aggregation/kafka/AggregationListeners` | Kafka Consumer | Consume `${ruleengine.rule-evaluated-topic}` | `stage_aggregate` |
| `aggregation/service/AggregationService` | Service | Deserialize `RuleEvaluatedEvent`, compute minute bucket, adjust counters | `stage_aggregate` |
| `aggregation/repository/StageAggregateRepository` | Repository (JdbcTemplate) | Upsert aggregates per workflowVersion/groupHash/node/bucket | `stage_aggregate` |
| `aggregation/web/AggregationController` | Controller | `/workflows/{id}/aggregates`, `/wallboard` queries | `stage_aggregate` |

### 4.4.3 Typical Class Flow
- Kafka message → `AggregationListeners.onRuleEvaluated` → `AggregationService.handleRuleEvaluated` → `StageAggregateRepository.upsert` for completed/late/failed and in-flight adjustments → REST queries via controller for latest buckets.

### 4.4.4 Database Tables
- `stage_aggregate`: minute buckets storing `in_flight`, `completed`, `late`, `failed`.

### 4.4.5 Example Usage
- `rule.evaluated` event with `inFlightDeltas={"ship":1}` and `lateDelta=0` → Aggregation upserts bucket for node `ship`, incrementing in-flight; `/wallboard` returns latest rows for dashboard rendering.

## 4.5 Package: com.sentinel.platform.alerting

### 4.5.1 Responsibility
- Persist and manage alert lifecycle triggered by rule engine outcomes; provide operator endpoints with audit. Infra/operations layer.

### 4.5.2 Key Classes
| Class Name | Type | Responsibility | Related Tables |
| --- | --- | --- | --- |
| `alerting/kafka/AlertingListeners` | Kafka Consumer | Consume `${ruleengine.alerts-triggered-topic}` | `alert` |
| `alerting/service/AlertingService` | Service | Upsert alerts from events; lifecycle methods for ack/suppress/resolve | `alert`, `audit_log` |
| `alerting/repository/AlertRepository` | Repository (JdbcTemplate) | Upsert by `dedupe_key`, update state with actor/timestamp | `alert` |
| `alerting/repository/AuditRepository` | Repository (JdbcTemplate) | Record lifecycle actions as JSON details | `audit_log` |
| `alerting/web/AlertController` | Controller | List alerts, ack/suppress/resolve endpoints with role checks | `alert`, `audit_log` |

### 4.5.3 Typical Class Flow
- Trigger event: `AlertingListeners.onAlertTriggered` → `AlertingService.handleAlertTriggered` → `AlertRepository.upsert` (dedupe on `dedupe_key`, update severity/last_triggered_at).
- Lifecycle: `AlertController` endpoint → `AlertingService.ack/suppress/resolve` → `AlertRepository.updateState` → `AuditRepository.record`.

### 4.5.4 Database Tables
- `alert`: open/ack/suppressed/resolved alerts with dedupe key, severity, timestamps.
- `audit_log`: audit entries for lifecycle changes.

### 4.5.5 Example Usage
- `alerts.triggered` message with `dedupeKey="v1:ship:ORD-1"` → `AlertRepository.upsert` opens/updates alert → operator POST `/alerts/{id}/ack` → state updated and audit row inserted with actor/reason.

## 4.6 Package: com.sentinel.platform.config

### 4.6.1 Responsibility
- Cross-cutting configuration for security and time. Infrastructure layer.

### 4.6.2 Key Classes
| Class Name | Type | Responsibility | Related Tables |
| --- | --- | --- | --- |
| `config/SecurityConfig` | Configuration | OAuth2 JWT resource server, expose `/actuator/health` anonymously, method security enabled | - |
| `config/TimeConfig` | Configuration | Provides UTC `Clock` bean used by services | - |
| `PlatformApplication` | Bootstrap | Enables scheduling and configuration properties for ingestion/rule engine | - |

### 4.6.3 Typical Class Flow
- Application startup loads `PlatformApplication` → enables scheduling → registers `IngestionProperties` and `RuleEngineProperties` → security filter chain configured via `SecurityConfig` for all endpoints.

### 4.6.4 Database Tables
- None directly.

### 4.6.5 Example Usage
- When `IngestionService` requests current time, it uses the injected UTC clock from `TimeConfig`; security checks for `/ingest` leverage JWT roles configured in `SecurityConfig`.

## 5. Configuration & Integrations

### 5.1 Configuration
- Application config: `backend/platform-service/src/main/resources/application.yml`.
- Database: `spring.datasource.*` env-driven (`DB_URL`, `DB_USER`, `DB_PASSWORD`), Flyway enabled with migrations `V0001`–`V0007`.
- Security: OAuth2 resource server issuer `OIDC_ISSUER_URI`; roles enforced via `@PreAuthorize`.
- Ingestion properties (`ingestion.*`): max concurrent REST requests, payload/group size limits, default source system, producer tuning (linger/retries/compression), normalized/DLQ topics.
- Rule engine properties (`ruleengine.*`): topic names for normalized, synthetic, rule-evaluated, alerts-triggered; scheduler enabled flag; interval and poll limit.
- Logging/metrics: Micrometer/Actuator exposed; log level per `com.sentinel.platform`.

### 5.2 External Integrations
- Kafka: consumes `events.raw` (Cloud Stream), `${ruleengine.normalized-topic}`, `${ruleengine.synthetic-topic}`, `${ruleengine.rule-evaluated-topic}`, `${ruleengine.alerts-triggered-topic}`; produces normalized events (`normalizedEvents-out-0`), DLQ (`dlq-out-0`), synthetic misses, rule evaluations, alerts. Configured via `application.yml` and properties classes.
- MariaDB: primary persistence for all modules; schemas defined via Flyway migrations under `src/main/resources/db/migration`.
- OAuth2/JWT: JWT validation for secured endpoints configured in `SecurityConfig`; issuer set via env.

## 6. Onboarding & Extension

### 6.1 For New Developers
- Read `docs/architecture.md` for context, then this guide for code-to-table mapping.
- Start in `PlatformApplication` to see enabled features, then review package sections above.
- Run quality gate locally: `cd backend/platform-service && ./mvnw test` (uses Testcontainers for MariaDB/Kafka).
- Exercise key endpoints: `/ingest`, `/workflows`, `/items/{correlationKey}`, `/alerts`, `/workflows/{id}/aggregates`.

### 6.2 How to Add a New Feature
- Decide entrypoint: REST controller or Kafka listener in appropriate package.
- Add service logic with clear separation from adapters; reuse UTC `Clock` and validators.
- Persist via existing repositories or add new JdbcTemplate/JPA repositories with migrations under `src/main/resources/db/migration`.
- Wire messaging topics via `application.yml` and, for Cloud Stream bindings, add bindings under `spring.cloud.stream.bindings`.
- Extend documentation here and, if schemas change, update `docs/architecture.md`; add tests to cover REST and Kafka paths.

## 7. Known Gaps / TODOs

- TODO: Planned future work – Replay/recovery and additional workflow graph validation endpoints are deferred to later phases.

## 8. Rule Engine Deep Dive

### 8.1 Entry Points
- Kafka normalized events: `RuleEngineListeners.onNormalized` listens on `${ruleengine.normalized-topic}` (default `events.normalized`) and deserializes `NormalizedEvent`.
- Synthetic misses: `RuleEngineListeners.onSyntheticMissed` listens on `${ruleengine.synthetic-topic}` (default `synthetic.missed`) carrying `SyntheticMissedEvent`.
- Scheduler: `ExpectationSchedulerService` polls the `expectation` table and sends `SyntheticMissedEvent` to Kafka.
- Timeline API: `/items/{correlationKey}` in `ItemController` reads `workflow_run`, `event_occurrence`, `expectation`, `alert`.

### 8.2 Workflow Resolution and State
- Workflow versions resolved by:
  - Explicit `workflowKeys` list in the event (fan-out).
  - Single `workflowKey` hint.
  - Otherwise, active workflows whose nodes match `eventType` (`WorkflowNodeRepository.findActiveByEventType`).
- Runtime state tables:
  - `workflow_run`: one row per `(workflow_version_id, correlation_key)`, tracks status and last node.
  - `event_occurrence`: each applied event with late/duplicate/order flags and payload excerpt.
  - `expectation`: pending/fired/cleared expectations per edge (`from_node_key`,`to_node_key`), due time, severity, lock owner, fired_at.

### 8.3 Evaluation Flow (per normalized event)
1) Find node for `eventType` in the target workflow version (`RuleEngineStateRepository.findNodeForEvent`).
2) Load or create `workflow_run` for `(workflowVersionId, correlationKey)` with status `green`.
3) Duplicate guard: if `eventId` seen for the run, event is ignored.
4) Clear expectations for the target node (one row at a time). A late flag is set if `receivedAt` > `due_at`. Order violation is suppressed when only optional inbound edges exist; otherwise flagged when no expectation cleared and node is not start.
5) Create new expectations for each outgoing edge unless `optional=true`. For `expectedCount`, multiple rows are created with the same due time. `absoluteDeadline` is parsed (HH:mm or offset) or `maxLatencySec` is added to `eventTime`.
6) Persist occurrence with flags; update run status (red on order violation, amber/red on late severity, green otherwise).
7) Emit `RuleEvaluatedEvent` to `${ruleengine.rule-evaluated-topic}` with deltas for completed/late/failed and in-flight adjustments.
8) If late or order violation, emit `AlertTriggerEvent` to `${ruleengine.alerts-triggered-topic}` with dedupe key `<workflowVersionId>:<node>:<correlationKey>`.

### 8.4 Synthetic Miss Handling
- Scheduler claims due `expectation` rows (`pending`) and marks them `fired` with lock owner.
- Each claimed row is serialized as `SyntheticMissedEvent` (includes `dueAt`, `severity`, `dedupeKey`) to `${ruleengine.synthetic-topic}`.
- `RuleEngineService.handleSyntheticMissed` loads run context, emits `RuleEvaluatedEvent` marked `late=true`, updates run status, and emits `AlertTriggerEvent` with reason `EXPECTED_MISSED`.

### 8.5 Kafka Message Schemas (Rule Engine)
- `events.normalized` (payload: `NormalizedEvent`)
  - `eventId` (string, optional; UUID if absent)
  - `sourceSystem` (string, required)
  - `eventType` (string, required)
  - `eventTime` (ISO-8601, required)
  - `receivedAt` (ISO-8601, optional; defaults to now)
  - `workflowKey` (string, optional)
  - `workflowKeys` (array<string>, optional)
  - `correlationKey` (string, required; Kafka key)
  - `group` (object, optional)
  - `payload` (object, optional)
- `synthetic.missed` (payload: `SyntheticMissedEvent`)
  - `expectationId` (long, required)
  - `workflowRunId` (long, required)
  - `fromNode` (string, required)
  - `toNode` (string, required)
  - `dueAt` (ISO-8601, required)
  - `severity` (string, required)
  - `dedupeKey` (string, required; Kafka key uses `toNode`)
- `rule.evaluated` (payload: `RuleEvaluatedEvent`)
  - `workflowVersionId` (long, required)
  - `workflowRunId` (long, required)
  - `node` (string, required)
  - `correlationKey` (string, required; Kafka key)
  - `status` (string `green|amber|red`, required)
  - `late` (bool), `orderViolation` (bool)
  - `completedDelta` (int), `lateDelta` (int), `failedDelta` (int)
  - `inFlightDeltas` (map<string,int>, optional)
  - `groupHash` (string, optional), `group` (object, optional)
  - `eventTime` (ISO-8601), `receivedAt` (ISO-8601)
- `alerts.triggered` (payload: `AlertTriggerEvent`)
  - `dedupeKey` (string, required; Kafka key)
  - `workflowVersionId` (long, required)
  - `workflowRunId` (long, optional)
  - `node` (string, required)
  - `correlationKey` (string, required)
  - `severity` (string, required)
  - `reason` (string, required; e.g., `SLA_MISSED`, `ORDER_VIOLATION`, `EXPECTED_MISSED`)
  - `triggeredAt` (ISO-8601, optional; defaults to now)

### 8.6 Wallboard/Timeline APIs
- `/workflows/{id}/aggregates`: returns rows from `stage_aggregate` filtered by workflow version and optional `groupHash`.
- `/wallboard`: returns recent `stage_aggregate` rows across workflows.
- `/items/{correlationKey}`: returns latest run for the key (or specific `workflowVersionId`), including events, remaining expectations, and alerts.

## 9. Aggregation Deep Dive

### 9.1 Entry Points and Flow
- Kafka listener `AggregationListeners.onRuleEvaluated` consumes `${ruleengine.rule-evaluated-topic}`.
- `AggregationService.handleRuleEvaluated` deserializes `RuleEvaluatedEvent`, computes minute bucket (based on `receivedAt` or now), and calls `StageAggregateRepository.upsert`.
- Upsert adjusts:
  - `in_flight` by `inFlightDeltas` per nodeKey.
  - `completed`, `late`, `failed` by the corresponding deltas on the event node.
- No latency metrics are computed (columns removed).

### 9.2 Persistence
- Table `stage_aggregate` columns: `workflow_version_id`, `group_dim_hash`, `node_key`, `bucket_start`, `in_flight`, `completed`, `late`, `failed`.
- Upsert key: `(workflow_version_id, group_dim_hash, node_key, bucket_start)`.
- `group_dim_hash` corresponds to `RuleEvaluatedEvent.groupHash` (SHA-256 first bytes of sorted group dims).

### 9.3 Exposure to Frontend
- `/workflows/{id}/aggregates`: direct select on `stage_aggregate` with optional `groupHash` filter and `limit`.
- `/wallboard`: latest rows ordered by `bucket_start desc limit ?`.
- Frontend wallboard tiles map `in_flight`, `completed`, `late`, `failed` per node; countdowns in UI derive from expectations (not provided by this API).

### 9.4 Kafka Message Schema (Aggregation)
- Consumed: `rule.evaluated` (see 8.5).
- Produced: none (aggregation writes DB only).

## 10. End-to-End Examples

### 10.1 Example 1: Trade Lifecycle
- Workflow config (POST `/workflows`):
  ```json
  {
    "name": "Trade Lifecycle",
    "key": "trade-lifecycle",
    "createdBy": "ops",
    "graph": {
      "nodes": [
        {"key":"ingest","eventType":"TRADE_INGEST","start":true},
        {"key":"sys2-verify","eventType":"SYS2_VERIFIED"},
        {"key":"sys3-ack","eventType":"SYS3_ACK"},
        {"key":"sys4-settle","eventType":"SYS4_SETTLED","terminal":true}
      ],
      "edges": [
        {"from":"ingest","to":"sys2-verify","maxLatencySec":300,"severity":"amber","expectedCount":2},
        {"from":"sys2-verify","to":"sys3-ack","maxLatencySec":300,"severity":"red"},
        {"from":"sys3-ack","to":"sys4-settle","absoluteDeadline":"08:00Z","severity":"amber","optional":true}
      ]
    },
    "groupDimensions":["book","region"]
  }
  ```
- Event stream (Kafka `events.raw` → ingestion → `events.normalized`):
  1) `TRADE_INGEST` (correlationKey `TR123`, group `{book:"EQD",region:"NY"}`) → Rule engine creates run, clears none, creates 2 expectations for `sys2-verify`.
  2) `SYS2_VERIFIED` → clears one expectation (late? based on due), creates expectation to `sys3-ack`.
  3) Second `SYS2_VERIFIED` (expectedCount=2) → clears the second expectation; no order violation.
  4) `SYS3_ACK` → clears expectation, creates optional expectation to `sys4-settle` (optional edge skipped for new expectations); no order violation.
  5) Missing `SYS4_SETTLED` before deadline → scheduler emits `synthetic.missed` → rule engine marks late, emits alert `EXPECTED_MISSED`.
- Sample normalized Kafka payloads (values abbreviated, topic `events.normalized`):
  1) `TRADE_INGEST`
     ```json
     {
       "eventId": "e1",
       "sourceSystem": "sys1",
       "eventType": "TRADE_INGEST",
       "eventTime": "2024-06-10T12:00:00Z",
       "receivedAt": "2024-06-10T12:00:02Z",
       "workflowKey": "trade-lifecycle",
       "correlationKey": "TR123",
       "group": {"book": "EQD", "region": "NY"},
       "payload": {"notional": 1000000}
     }
     ```
  2) `SYS2_VERIFIED` (first)
     ```json
     {
       "eventId": "e2",
       "sourceSystem": "sys2",
       "eventType": "SYS2_VERIFIED",
       "eventTime": "2024-06-10T12:02:00Z",
       "receivedAt": "2024-06-10T12:02:01Z",
       "workflowKey": "trade-lifecycle",
       "correlationKey": "TR123",
       "group": {"book": "EQD", "region": "NY"}
     }
     ```
  3) `SYS2_VERIFIED` (second)
     ```json
     {
       "eventId": "e3",
       "sourceSystem": "sys2",
       "eventType": "SYS2_VERIFIED",
       "eventTime": "2024-06-10T12:02:05Z",
       "receivedAt": "2024-06-10T12:02:06Z",
       "workflowKey": "trade-lifecycle",
       "correlationKey": "TR123",
       "group": {"book": "EQD", "region": "NY"}
     }
     ```
  4) `SYS3_ACK`
     ```json
     {
       "eventId": "e4",
       "sourceSystem": "sys3",
       "eventType": "SYS3_ACK",
       "eventTime": "2024-06-10T12:04:00Z",
       "receivedAt": "2024-06-10T12:04:01Z",
       "workflowKey": "trade-lifecycle",
       "correlationKey": "TR123",
       "group": {"book": "EQD", "region": "NY"}
     }
     ```
  5) Synthetic miss emitted by scheduler (topic `synthetic.missed`)
     ```json
     {
       "expectationId": 42,
       "workflowRunId": 1,
       "fromNode": "sys3-ack",
       "toNode": "sys4-settle",
       "dueAt": "2024-06-10T08:00:00Z",
       "severity": "amber",
       "dedupeKey": "exp-42-1718006400000"
     }
     ```
- Event-by-event system effects (wallboard and DB):
  1) After `TRADE_INGEST`:
     - DB: `workflow_run` created (status `green`); two `expectation` rows for `sys2-verify`.
     - Aggregation: `rule.evaluated` for node `ingest` increments `completed=1`, `in_flight` +2 for `sys2-verify` bucket.
     - Wallboard (`/workflows/{id}/aggregates?groupHash=<EQD/NY hash>`): shows `ingest.completed=1`, `sys2-verify.in_flight=2`.
  2) First `SYS2_VERIFIED`:
     - DB: one expectation cleared; run stays `green`; new expectation to `sys3-ack`.
     - Aggregation: node `sys2-verify` `completed=1`, `in_flight` adjustments (-1 for `sys2-verify`, +1 for `sys3-ack`).
     - Wallboard: `sys2-verify.completed=1`, `sys2-verify.in_flight=1`, `sys3-ack.in_flight=1`.
  3) Second `SYS2_VERIFIED`:
     - DB: second expectation cleared; no order violation; run `green`.
     - Aggregation: another `completed=1` on `sys2-verify`, `in_flight` -1 on `sys2-verify`.
     - Wallboard: `sys2-verify.completed=2`, `sys2-verify.in_flight=0`.
  4) `SYS3_ACK`:
     - DB: expectation to `sys3-ack` cleared; optional edge means no new expectations; run `green`.
     - Aggregation: `sys3-ack.completed=1`, `sys3-ack.in_flight` -1; no late/failed.
     - Wallboard: `sys3-ack.completed=1`, `sys3-ack.in_flight=0`.
  5) Synthetic miss for `SYS4_SETTLED`:
     - Scheduler claims pending expectation, marks `fired`, emits `synthetic.missed`.
     - Rule engine marks late for node `sys4-settle`, updates run status `amber/red`, emits `rule.evaluated` (lateDelta=1, failedDelta=0) and `alerts.triggered` (`EXPECTED_MISSED`).
     - Aggregation: `sys4-settle.late=1`, `in_flight` unchanged (optional edge skipped at creation).
     - Wallboard: `sys4-settle.late=1`, alert appears in `/items/TR123` alerts list; overall workflow tile may show degraded status depending on frontend logic.
- State and DB touchpoints:
  - `event_raw`: one row per ingest (idempotent on `source_system`,`eventId`).
  - `workflow_run`: one row for `trade-lifecycle` + `TR123`, status transitions green → green → green → green → amber/red on miss.
  - `event_occurrence`: one per received event with late/order flags.
  - `expectation`: rows created/cleared/fired per edge; two rows for first edge due to `expectedCount=2`.
- Kafka emissions:
  - After each applied event: `rule.evaluated` with `completedDelta=1`, in-flight deltas reflecting expectation changes, keyed by `correlationKey`.
  - On late/order: `alerts.triggered` with `reason` (`SLA_MISSED` or `EXPECTED_MISSED`), dedupe key `<versionId>:<node>:<correlationKey>`.
- Aggregation:
  - Consumes each `rule.evaluated`, upserts `stage_aggregate` minute bucket per `workflowVersionId`, `groupHash` (hash of `{book,region}`), `node`.
  - `in_flight` increments by expected counts; decrements when expectations clear.
- Wallboard:
  - Frontend calls `/workflows/{id}/aggregates?groupHash=<hash>`; sees per-node counts for latest buckets (in-flight decreases as expectations clear; late increments on synthetic miss).
  - Timeline `/items/TR123` shows events, remaining expectations (if any), and alerts list from `alert` table.
- Example alert payload (Kafka `alerts.triggered`):
  ```json
  {
    "dedupeKey":"<wfVersionId>:sys4-settle:TR123",
    "workflowVersionId":1,
    "workflowRunId":1,
    "node":"sys4-settle",
    "correlationKey":"TR123",
    "severity":"amber",
    "reason":"EXPECTED_MISSED",
    "triggeredAt":"2024-06-10T12:05:00Z"
  }
  ```

### 10.2 Example 2: Parallel Trade + Equity Workflows
- Config snippets:
  - Trade workflow as above.
  - Equity workflow (POST `/workflows`):
    ```json
    {
      "name":"Equity Allocation",
      "key":"equity-flow",
      "createdBy":"ops",
      "graph":{
        "nodes":[
          {"key":"alloc-received","eventType":"EQUITY_ALLOC","start":true},
          {"key":"alloc-validated","eventType":"EQUITY_VALIDATED"},
          {"key":"alloc-booked","eventType":"EQUITY_BOOKED","terminal":true}
        ],
        "edges":[
          {"from":"alloc-received","to":"alloc-validated","maxLatencySec":120,"severity":"amber"},
          {"from":"alloc-validated","to":"alloc-booked","maxLatencySec":300,"severity":"red"}
        ]
      },
      "groupDimensions":["desk","region"]
    }
    ```
- Ingestion at volume:
  - Trades and equities both publish to `events.raw` with distinct `eventType`s and `workflowKey` hints (`trade-lifecycle` vs `equity-flow`).
  - Ingestion normalizes and keys by `correlationKey` (e.g., tradeId, allocId). Kafka partitions keep per-key ordering.
- Routing:
  - `RuleEngineService.handleNormalizedEvent` resolves by `workflowKey`; trade events only touch trade workflow; equity events only touch equity workflow.
  - Runs, occurrences, expectations, and alerts are isolated per `(workflowVersionId, correlationKey)`.
- Aggregation separation:
  - `stage_aggregate.workflow_version_id` distinguishes trade vs equity aggregates.
  - `group_dim_hash` reflects workflow-specific group dims (`book/region` vs `desk/region`), so wallboard queries per workflow return independent counts.
- Wallboard display:
  - Frontend can query `/workflows/{tradeVersionId}/aggregates` and `/workflows/{equityVersionId}/aggregates` to render separate tiles/cards.
  - Filters by `groupHash` isolate desks/regions per workflow.
- Kafka outputs:
  - `rule.evaluated` and `alerts.triggered` carry `workflowVersionId` and `correlationKey`, allowing downstream consumers to distinguish trade vs equity workflows.
  - Example equity alert payload:
    ```json
    {
      "dedupeKey":"<equityVersionId>:alloc-booked:EQ-555",
      "workflowVersionId":2,
      "workflowRunId":15,
      "node":"alloc-booked",
      "correlationKey":"EQ-555",
      "severity":"red",
      "reason":"SLA_MISSED",
      "triggeredAt":"2024-06-10T12:07:00Z"
    }
    ```

### 10.3 Example 3: Multiple Trades in One Workflow (wallboard grouping)
- Scenario: Same Trade Lifecycle workflow as 10.1 with three trades arriving interleaved: `TR123`, `TR456`, `TR789`.
- Ingestion:
  - All events flow through `events.raw` → `events.normalized` with `workflowKey="trade-lifecycle"` and `correlationKey` per trade.
  - Kafka partitions ensure per-correlation ordering; rule engine creates independent `workflow_run` rows for each trade.
- Grouping and wallboard:
  - Group hash derived from group dims (e.g., `{book:"EQD",region:"NY"}` → hash A; `{book:"EQD",region:"LN"}` → hash B).
  - `/workflows/{id}/aggregates?groupHash=<hash>` filters per group; `/wallboard` shows latest buckets per group.
- Example flow:
  - `TR123` (EQD/NY) receives `TRADE_INGEST` then two `SYS2_VERIFIED` events: wallboard shows hash A with `ingest.completed=1`, `sys2-verify.completed=2`, `sys2-verify.in_flight=0`.
  - `TR456` (EQD/LN) only has `TRADE_INGEST` so far: wallboard hash B shows `sys2-verify.in_flight=2` (expectedCount=2) waiting for validations.
  - `TR789` (EQD/NY) ingested but no downstream events yet: hash A `sys2-verify.in_flight` increases by 2 (total for hash A reflects TR123 completed expectations plus TR789 outstanding ones).
- Ops checks for a specific trade (e.g., TR456):
  - Call `/items/TR456` to see `workflowVersionId`, run status, events applied, pending expectations (two `sys2-verify` rows), and any alerts.
  - If late or out-of-order occurs, an alert will also surface via `/alerts` (optionally filtered by state) and appear in the timeline `alerts` array.
