# Playwright Regression Suite

## Purpose
- Boots infra + backend with `scripts/start.sh` (using `SKIP_FRONTEND=true`) and seeds demo data before every run.
- Launches the Angular portal in mock-friendly mode via Playwright `webServer` to exercise UI flows deterministically while the backend remains available for API parity.
- Captures screenshots, videos, and auto-generated GIFs for production support sign-off across all core features.

## Execution
1. `cd tests/regression`
2. `npm test`  
   - Global setup installs deps if missing, starts infra/backend (`../scripts/start.sh`), seeds data (`../scripts/seed.sh`), then Playwright serves the Angular app on `localhost:4300`.
   - Override ports with `BACKEND_PORT`/`FRONTEND_PORT`; skip components with `SKIP_BACKEND`/`SKIP_FRONTEND`; override the UI command with `FRONTEND_START_CMD` if pointing at a gateway build.
3. Optional coverage run: `npm run test:coverage` (nyc-wrapped Playwright).
4. Stop lingering processes (if the run was interrupted): `../scripts/stop.sh`.

```mermaid
flowchart LR
  setup[globalSetup] --> infra[Start infra + backend (start.sh SKIP_FRONTEND)]
  infra --> seed[Seed demo data]
  seed --> ui[Launch Angular dev server\n(mock API enabled)]
  ui --> suites[Playwright suites\nwallboard/alerts/rules/ingest/workflow/item]
  suites --> artifacts[Artifacts: screenshots + videos + GIFs]
```

## Coverage (happy-path + ops views)
- **Workflow CRUD/readiness**: create workflow via `/rules`, verify catalogue entry, drill into `/workflow/:key` stage tiles/graph.
- **Wallboard**: production-support metrics (In Flight/Late/Failed), countdown badges, group drill-down to workflow view.
- **Alerts console**: ack → suppress → resolve lifecycle with runbook visibility.
- **Rules form usage**: add nodes/edges, preview graph updates, submit new definitions.
- **Live ingest**: submit events via `/ingest`, observe wallboard metric bump and `/item/:correlationKey` timeline capture.
- **Item timeline**: expectations, alerts, hop-by-hop events remain visible after refresh.

## Artifacts
- Screenshots: `tests/regression/test-results/artifacts/*.png`.
- Videos: Playwright defaults under `tests/regression/test-results/**/video.webm`.
- GIF exports: `tests/regression/test-results/artifacts/gifs/*.gif` (generated in global teardown via `ffmpeg-static`).
- HTML report: `tests/regression/playwright-report/index.html` (not auto-opened).

## Notes & toggles
- Tests run **serially** (`fullyParallel:false`) to reuse seeded data and deterministic mock responses.
- Base URL can be overridden with `PLAYWRIGHT_BASE_URL` if you need a different frontend port.
- UI currently runs with the mock API profile; to point at a gateway build, supply a custom `FRONTEND_START_CMD` that serves a non-mock build and flip `environment.mockApi` accordingly before running the suite. 
