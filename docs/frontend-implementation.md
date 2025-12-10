# Frontend Implementation Notes

## Overview
- Angular 20 SPA built for the command-center wallboard, workflow drill-in, item timeline, alerts console, rules authoring, and ingest simulator.
- Aligns to `docs/architecture.md` and `docs/delivery-plan.md` phase 8 by wiring WebSocket/polling façade (`LiveUpdateService`), mock-backed REST client, PLC-like tiles/graph canvas, and alert ack/suppress/res flows.
- Default dev profile runs fully offline via the mock API interceptor while keeping base URLs configurable for the real platform gateway.

## Feature Coverage (delivery plan mapping)
- Wallboard (phase 6/8): auto-refreshed tiles, countdown badges, status pills, wallboard/UTC toggles.
- Workflow view (phase 4/6/8): graph canvas, per-stage tiles with aggregates, group selector, alert strip.
- Item timeline (phase 4/5/8): hop-by-hop events, pending expectations, linked alerts, countdown.
- Alerts console (phase 7/8): list + ack/suppress/resolve actions with optimistic UI updates.
- Rules (phase 3/8): create workflow graphs with nodes/edges, preview canvas, catalog list.
- Ingest simulator (phase 2): POST `/ingest` helper for ops smoke tests using the mock API.

## Data Flow (mock + real API)
```mermaid
flowchart LR
  UI[Angular views] -->|HttpClient| API[PlatformApiService]
  API -->|mockApi=true| Mock[MockApiInterceptor\n+ MockBackendService]
  API -->|mockApi=false| Gateway[(UI Gateway / REST)]
  Live[LiveUpdateService\n(poll + push-ready)] --> UI
  Mock --> Wallboard
  Gateway -.-> Kafka[Kafka/read models]:::faded
  classDef faded fill:#0d1320,stroke:#4c566a,color:#7d8ea8;
```
- Toggle mock mode in `src/environments/environment.ts` (`mockApi: true` by default). Set `apiBaseUrl`/`wsBaseUrl` when pointing at the platform gateway.
- Logging is enabled on every HTTP request for quick triage.

## Routes & Components
- `/wallboard` → wallboard tiles with countdowns and status pills.
- `/workflow/:key` → lifecycle graph (SVG canvas), stage tiles, alerts.
- `/item/:correlationKey` → lifecycle timeline with expectations and alerts.
- `/alerts` → alert console with lifecycle actions.
- `/rules` → workflow catalog + authoring form + preview canvas.
- `/ingest` → ingest simulator form for ops smoke tests.
- Shared building blocks: status pill, countdown badge, stage tile, graph canvas, lifecycle timeline, alert strip, toolbar.

## Testing & Coverage
- Unit tests: `cd frontend && npm test` (ChromeHeadless) with mocks + interceptors wired.
- Playwright regression (stack bootstrap + artifacts): `cd tests/regression && npm test` (global setup installs deps, builds backend if needed, starts infra + backend + demo-mode Angular via `scripts/start.sh`, seeds data, then runs Playwright against the live API). Captures screenshots, videos, and GIFs under `tests/regression/test-results/`; coverage variant remains available via `npm run test:coverage`.
- Notes: Angular compiler emits optional chaining warnings only; no test failures. Demo data covers trade lifecycle and file receipt workflows plus sample alerts/expectations.

## Assumptions & Next Steps
- Auth is stubbed (`AuthService` keeps a local user/token); wire to Keycloak/OIDC when gateway auth is ready.
- WebSocket URL is configurable but polling remains the default for local runs; swap to gateway push once available.
- For production, flip `mockApi` to `false`, set gateway URLs, and revisit alert/reporting coverage to pull from live read models.
