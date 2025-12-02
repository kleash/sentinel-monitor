# Event-Based Monitoring System — Problem Statement

## Purpose and Context
- Provide Production Support with near real-time visibility into business and technical workflows, highlighting SLA breaches and bottlenecks.
- Centralize monitoring across heterogeneous systems (file drops, trade pipelines, downstream acknowledgements) using an event-driven model rather than bespoke point solutions.

## Goals
- Configurable monitoring workflows defined as ordered or branching event graphs with timing rules.
- Detect and alert on missing, late, or unexpected events; track counts and progression by cohort (e.g., job run, file batch, trade).
- Enable lifecycle tracing for an individual item (e.g., trade) and aggregate views for throughput, backlog, and SLA status.
- Support rule authoring without code deployments (UI + API).

## Non-Goals
- Orchestrating or executing the underlying business processes.
- Building a full-featured analytics warehouse; focus is operational monitoring and alerting.

## Users and Stakeholders
- Production Support / NOC: live status, triage, acknowledgements.
- Run teams / Ops engineers: configure rules and workflows, tune thresholds.
- App teams: instrument publishers; provide schemas and identifiers.

## Core Use Cases
1) File receipt SLA: expect file `X` by 08:00; if absent or late, mark job red and alert.
2) Trade lifecycle: Trade Ingest → Verify in System 2 → Send to System 3 → await reply → Send to System 4. Monitor counts at each stage, detect stalls, and allow per-trade lifecycle view.
3) Sequenced batches: After Event A completes, require Event B within N minutes; then Event C; raise alerts on gaps or order violations.
4) Grouped metrics: For a group (e.g., market, region, product), track volume, success/fail counts, and aging of in-flight items.

## Problem Definition
- Heterogeneous event sources and schedules lack a unified, configurable monitoring surface.
- Missing or late events are currently detected manually; sequencing logic is embedded in code or runbooks.
- Production Support lacks item-level traceability and aggregate bottleneck detection across systems.

## Functional Requirements
- Event ingestion: accept events via Kafka validate and normalize.
- Correlation: support unique keys (e.g., tradeId, batchId, jobId) and grouping dimensions (e.g., market, system).
- Workflow modeling: define directed graphs with states/edges; support linear and branching paths; allow optional/alternate steps.
- Timing rules: per edge SLA (e.g., next event must arrive within N minutes/hours) and absolute-time SLAs (e.g., by 08:00 local).
- Presence/absence detection: trigger alerts when expected events do not occur by deadline or interval.
- Ordering enforcement: detect out-of-order events or duplicates; configurable handling (drop, flag, alert).
- Aggregations: maintain counts, rates, backlog, and aging per workflow, stage, and grouping key.
- Lifecycle view: reconstruct timeline for a specific item; show timestamps, duration per stage, last event payload excerpt.
- Alerting: configurable channels (email and DL); severity levels; suppressions, maintenance windows, and deduplication.
- Status model: green/amber/red per workflow, stage, and group; derived from SLAs and rule violations.
- Configuration: versioned rules; UI and API for create/read/update; validation and dry-run/simulate mode.
- Observability: dashboards for current status and trends; searchable event log with filter.
- Audit: record who changed rules, when, and publish before/after diff.

## Observability and Command-Center UI (PLC-Like)
- Visual workflow maps that mirror configured lifecycle graphs; each node/edge uses green/amber/red to show SLA state, with pulsing/blink on red for attention.
- Big-screen/NOC mode: auto-refreshing, high-contrast layout optimized for wallboards; shows top-level workflows with drill-in to groups (e.g., book/region) and per-stage counts/backlog.
- Per-stage tiles: in-flight, completed, late, and failed counts; aging buckets; timers counting down to next expected event or absolute SLA (e.g., file due 08:00).
- Alert strip and acknowledgment: surface active alerts with severity, auto-deduped; allow ack/suppress with reason; link to runbook; show time since breach.
- Item lifecycle view: click-through from any tile to a specific key (tradeId/jobId) to see hop-by-hop timestamps, payload excerpt, and time spent per stage.
- Anomaly signals: highlight rate drops/spikes vs. baseline and stalled stages; surface correlated logs/metrics when available.

## Non-Functional Requirements
- Reliability: at-least-once event handling with idempotency; durable storage; recoverable timers.
- Latency: near real-time evaluation (target sub-seconds to a few seconds for most paths).
- Scalability: handle high-volume streams (10^5–10^6 events/day baseline; bursty workloads).
- Time handling: consistent UTC storage with timezone-aware SLA windows.
- Security: authn/authz for UI/API; encrypted in transit and at rest; PII minimization.
- Operability: health checks, metrics, and structured logs; graceful degradation if a source is unavailable.
- Retention: configurable data retention for raw events vs. aggregates; archival policies.

## Key Concepts and Data
- Event: structured record with type, timestamp (source and received), correlation keys, payload, and source metadata.
- Workflow: graph of expected event types and transitions with timing/ordering rules.
- Cohort/Group: shared dimension (e.g., file name + date, trade book + region) for aggregation and status.
- SLA: deadline expressed as relative (after prior event) or absolute (clock time); includes severity and escalation path.
- Timer/Expectation: scheduled check derived from rules to detect missing/late events.
- Alert: emitted on rule violation; includes context (workflow, keys, offending rule), runbook link, and suppression state.

## Example Scenarios
- File receipt: Expect `file_received(jobId=DAILY_FEED, date=D)` by 08:00; if not seen, timer fires, workflow status red, alert created. On arrival after deadline, mark late, show delta.
- Trade pipeline: Ingest event starts workflow; each downstream event advances state; if System 3 reply absent within 5 minutes, mark that stage amber/red and alert; lifecycle view shows per-trade durations.

## Interfaces (High-Level)
- Ingestion endpoints: Kafka.
- Configuration API/UI: create workflows, rules, and SLAs; enable/disable; simulate with historical events.
- Query UI/API: dashboards, stage counts, backlog, item-level trace, alert console.
- Notification adapters: email, SMS, chat, webhook (for paging tools).

## Assumptions
- Event producers can supply stable correlation keys; clock skew is manageable or corrected at ingest.
- Downstream systems can emit at least minimal status events; where not, synthetic timers can represent expected responses.
- Daylight savings handled via timezone-aware schedules; storage in UTC.

## Open Questions
- Target throughput and peak rates per workflow? Acceptable alert latency?
- Required retention for raw events vs. aggregates? Need for cold storage?
- Must the system host a UI, or is API-first sufficient with existing dashboards?
- How strict should ordering be across partitions (e.g., per key ordering vs. global)?
- Which notification channels and paging tools are mandatory at launch?
