# Playwright Regression Suite

## Purpose
- Boots infra + backend **and** demo-mode Angular (proxied to the live API) with `scripts/start.sh`, then seeds demo data before every run.
- Exercises the UI against the real backend (no mock API) to validate wallboard metrics, correlation drill-downs, lifecycle views, and workflow CRUD flows end-to-end.
- Captures screenshots, videos, and auto-generated GIFs for production support sign-off across all core features.

## Execution
1. `cd tests/regression`
2. `npm test`  
   - Global setup installs deps if missing, builds backend jar if absent, starts infra/backend + Angular demo UI via `../scripts/start.sh`, then seeds data (`../scripts/seed.sh`).
   - Override ports with `BACKEND_PORT`/`FRONTEND_PORT`; skip components with `SKIP_BACKEND`/`SKIP_FRONTEND`; override the UI command with `FRONTEND_START_CMD` if pointing at a gateway build.
3. Optional coverage run: `npm run test:coverage` (nyc-wrapped Playwright).
4. Stop lingering processes (if the run was interrupted): `../scripts/stop.sh`.

```mermaid
flowchart LR
  setup[globalSetup] --> infra[Start infra + backend + demo UI (start.sh)]
  infra --> seed[Seed demo data]
  seed --> suites[Playwright suites\nwallboard/alerts/rules/ingest/workflow/item]
  suites --> artifacts[Artifacts: screenshots + videos + GIFs]
```

## Coverage (happy-path + ops views)
- **Workflow CRUD/readiness**: create workflow via `/rules`, verify catalogue entry, drill into `/workflow/:key` stage tiles/graph.
- **Wallboard**: production-support metrics (In Flight/Late/Failed), countdown badges, date filters, correlation drill-down to lifecycle views.
- **Alerts console**: ack → suppress → resolve lifecycle.
- **Rules form usage**: add nodes/edges, preview graph updates, submit new definitions.
- **Live ingest**: submit events via `/ingest`, observe wallboard metric bump and `/item/:correlationKey` timeline capture.
- **Item timeline**: expectations, alerts, hop-by-hop events remain visible after refresh.

## Artifacts
- Screenshots: `tests/regression/test-results/artifacts/*.png`.
- Videos: Playwright defaults under `tests/regression/test-results/**/video.webm`.
- GIF exports: `tests/regression/test-results/artifacts/gifs/*.gif` (generated in global teardown via `ffmpeg-static`).
- HTML report: `tests/regression/playwright-report/index.html` (not auto-opened).

## Notes & toggles
- Tests run **serially** (`fullyParallel:false`) to reuse seeded data deterministically.
- Base URL can be overridden with `PLAYWRIGHT_BASE_URL` if you need a different frontend port.
- UI runs with the demo profile (real backend). To target a gateway build, supply a custom `FRONTEND_START_CMD` and adjust Angular environment to disable mock API. 
