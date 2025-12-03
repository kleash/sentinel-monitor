# Automation Agent Ground Rules

Use these guardrails when contributing changes as an automated assistant. Stay aligned with the existing documents in `docs/` (problem statement, architecture, delivery plan) and the notes below.

## Repository Orientation
- Backend: `backend/` holds the Spring Boot services.
- Frontend: `frontend/` contains the Angular 20 application.
- Regression: `tests/regression/` contains the regression suite to bootstrap the application and run integration regression testing.
- Documentation: centralized under `docs/`.
- Historical context: `docs/problem-statement.md`.
- Ignore: skip the `prompts/` directory.

## Contribution Checklist
- Plan before you code; review related wiki/docs to match platform patterns.
- Keep documentation in sync: update `docs/` when features, workflows, or onboarding change.
- Prefer incremental commits with descriptive messages grouped by logical change.
- Keep test cases up to date across backend, frontend, automation smoke, example integration harness, and bootstrap scripts.
- Make sure to add intensive code comments and logging for easier understanding and maintainability.

## Quality Gates
- Backend: `cd backend && ./mvnw test`.
- Frontend: `cd frontend && npm test` (Karma launches `ChromeHeadlessNoSandbox` via npm script).
- Automation Regression (Playwright): `cd tests/regression`.

## Documentation Standards
- You must update documentation to keep track of what is implemented.
- Write Markdown with clear headings; use Mermaid diagrams for complex flows when possible.
- Link to related wiki sections using relative paths.
- Keep `README` concise; move deep dives to `docs/`.

## Working Rules for the Automation Agent
- Honor architectural constraints: Kafka-first ingest with REST fallback, idempotent handlers, durable expectations/timers, UTC storage.
- Follow delivery-phase readiness: don’t advance features that skip required prerequisites (e.g., scheduler without expectations).
- Respect repository boundaries: never read or write under `prompts/`; use `docs/` for any new guidance.

## Closing Notes
- Regularly update `AGENTS.md` as workflows evolve or new automation/coding standards are introduced.
