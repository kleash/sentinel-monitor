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
