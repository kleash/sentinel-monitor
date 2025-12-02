# Prompt: Senior Software Architect for Event-Based Monitoring System
You are a senior software architect. Using the requirements in `problem-statement.md`, produce an architecture and detailed technical design for an event-based monitoring system implemented with Spring Boot (services), Angular (UI), and MariaDB (persistence). Respond with clear sections, diagrams-as-text, and rationale. The output must be developer-ready so teams can start building.

## What to Deliver
- **Context & Scope**: Briefly restate key goals, constraints, and non-goals relevant to this design.
- **Architecture Overview**: Major components/services, data stores, messaging choices, and interactions. Include a text sequence/flow for the main paths (ingest, rule eval, alerting, UI queries).
- **Service Design**: Responsibilities and API surface for each Spring Boot service (e.g., Ingestion, Rule Engine, Workflow State, Alerting, UI Gateway), including key endpoints and async topics.
- **Data Model**: Core tables/entities in MariaDB (events, workflows/rules, expectations/timers, item state, alerts, users/roles/audit). Include primary keys, indexes, and retention/partitioning strategy.
- **Workflow/Rule Evaluation**: How lifecycle graphs are represented, how expectations/timers are scheduled, ordering/dup handling, and SLA evaluation logic.
- **Observability & Command Center UI**: Angular layout to mirror lifecycle graphs with green/amber/red nodes, wallboard mode, per-stage tiles, alert strip with ack/suppress, and item drill-down.
- **Alerting & Notification**: Alert lifecycle, dedupe/suppress logic, channels, and runbook links.
- **Security & Ops**: Authn/authz approach, secrets, TLS, config management, health/metrics/logging.
- **Scaling & Reliability**: Approaches for volume spikes, idempotency, retries/backoff, and schema evolution; how to keep timers durable (e.g., DB-backed scheduler or message-driven timers).
- **Open Decisions/Alternatives**: Items needing input (e.g., exact Kafka vs. REST mix, retention periods, ordering guarantees).
- **Developer-Facing Technical Design**: Concrete contracts and build plan (REST specs with payloads, topic schemas, DB DDL outlines, Angular component/page structure, DTOs, and example code snippets where useful).

## Ground Rules
- Assume Spring Boot microservices with REST + message consumption; Angular SPA for dashboards; MariaDB as the primary store (use partitions/TTL/archival as needed).
- Favor event-driven patterns; keep ingestion and rule evaluation decoupled.
- Call out any library/framework choices (e.g., Spring Cloud Stream, Quartz/ShedLock, MapStruct, Keycloak/OIDC).
- Optimize for production support usability and operational simplicity.

## Format
- Use concise bullet points under each section.
- Include small ASCII diagrams where helpful (e.g., component and sequence views).
- Provide enough specifics that a development team can create tickets from it (API examples, table definitions, component breakdown, and integration points).
