# Prompt: Senior Technical Lead — Delivery Plan
You are a senior technical lead. Using `architecture.md` (source of truth) and `problem-statement.md`, produce a concrete, build-ready delivery plan in markdown file for the event-based monitoring system (Spring Boot services, Angular UI, MariaDB, Kafka). DO NOT READ prompts folder and files inside it.

## What to Deliver (Markdown)
- **Assumptions & Dependencies**: clarify any pre-reqs (Kafka, Keycloak/SSO, CI/CD, infra).
- **Milestones/Phases**: sequenced phases with entry/exit criteria (e.g., env/bootstrap, ingest path, rule engine, timers, alerting, UI, hardening).
- **Epics & Stories**: per phase, list epics with clear developer stories/tasks and acceptance criteria; call out parallelizable vs. blocked items.
- **Service Work Breakdown**: per service (Ingestion, Rule Config, Rule Engine, Expectation Scheduler, Aggregation, Alerting, UI Gateway, Angular UI), enumerate build steps (APIs/topics/entities, tests, resiliency, observability).
- **Data & Schema Tasks**: DDL/Flyway plan for MariaDB tables/partitions/archival; seed data; schema registry artifacts for Kafka.
- **Cross-Cutting**: security (OIDC/JWT), logging/metrics/tracing, config management, CI/CD pipelines, idempotency/retry/DLQ handling, performance/scale tests.
- **Test Strategy**: unit/contract/integration/E2E, replay tests for rule evaluation, UI wallboard tests; data backfill/replay procedure.
- **Release & Ops Readiness**: runbooks (deploy, rollback, replay, DLQ drain), feature flags, health checks, capacity/retention configs.
- **Open Risks/Decisions**: any remaining gaps to resolve before/while building.

## Ground Rules
- Base all tasks on `architecture.md`; do not redesign architecture.
- Prefer actionable language (“Implement…”, “Create…”, “Add test for…”); keep items developer-ready.
- Assume Kafka per-key ordering only, email-only notifications at launch, WebSocket UI push, configurable 30-day hot retention with archival tables, single-tenant, UTC storage/UI-local display.
- Keep the plan concise but sufficient for ticket creation.

