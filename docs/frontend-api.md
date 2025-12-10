# Frontend API Guide (Platform Service)

All APIs secured via OIDC JWT with roles `viewer`, `operator`, `config-admin`. Base path is root (same host). All timestamps are UTC; UI should display local time with UTC toggle.

## Ingestion
- `POST /ingest` (roles: `operator`/`config-admin`)
  - Body: `{"eventId"?:string,"sourceSystem":string,"eventType":string,"eventTime":ISO,"correlationKey":string,"workflowKey"?:string,"group"?:object,"payload"?:object}`
  - Headers: `Idempotency-Key` optional to reuse as `eventId`.
  - Returns normalized event (includes `receivedAt`).

## Rule Config
- `GET /workflows` (roles: `viewer`/`config-admin`)
- `GET /workflows/{key}` (roles: `viewer`/`config-admin`)
- `POST /workflows` (role: `config-admin`)
  - Responses include the active version label plus the stored graph (nodes/edges/groupDimensions) for rendering workflow tiles and graphs.
  - Body example:
  ```json
  {
    "name": "Trade Lifecycle",
    "key": "trade-lifecycle",
    "createdBy": "alice",
    "graph": {
      "nodes": [
        {"key":"ingest","eventType":"TRADE_INGEST","start":true},
        {"key":"sys2-verify","eventType":"SYS2_VERIFIED"},
        {"key":"sys3-ack","eventType":"SYS3_ACK","terminal":true}
      ],
      "edges": [
        {"from":"ingest","to":"sys2-verify","maxLatencySec":300,"severity":"red"},
        {"from":"sys2-verify","to":"sys3-ack","maxLatencySec":300,"severity":"amber"}
      ]
    }
  }
  ```
  - Creates workflow + active version + nodes/edges.

## Read Models
- `GET /items/{correlationKey}?workflowVersionId=` (roles: `viewer`/`operator`/`config-admin`)
  - Returns latest workflow run for the correlation key: status, group, `events` (node, eventTime, receivedAt, lateness/order flags), `expectations`, `alerts`.
- `GET /workflows/{id}/aggregates?groupHash=&limit=50` (roles: `viewer`/`operator`/`config-admin`)
  - Rows from `stage_aggregate` with in-flight/completed/late/failed per bucket. Use `groupHash` to scope to a group.
- `GET /wallboard?limit=200` (roles: `viewer`/`operator`/`config-admin`)
  - Wallboard view composed from the latest aggregates: `{"updatedAt": ISO, "workflows":[{"workflowId","workflowKey","name","status","groups":[{"label","groupHash","status","inFlight","late","failed","countdowns":[]}]}]}`.
  - Group labels are derived from stored workflow run group dimensions (hash → key/value label), and statuses roll up worst-late/failed per group.

## Alerts
- `GET /alerts?state=open&limit=100` (roles: `viewer`/`operator`/`config-admin`)
  - Lists alerts; includes severity/state/timestamps/dedupe key.
- `POST /alerts/{id}/ack`
- `POST /alerts/{id}/suppress` (optional body `{reason, until: ISO}` to suppress until time)
- `POST /alerts/{id}/resolve`
  - Roles: `operator`/`config-admin`; all accept optional `{reason}`. 404 if alert not found.

## Topics (Kafka)
- Input: `events.raw` (ingestion), `events.normalized` (rule engine), `synthetic.missed` (scheduler), `rule.evaluated` (aggregation), `alerts.triggered` (alerting).
- DLQ: `events.dlq` for invalid/failed ingest.
- Keys: `correlationKey`.

## Notes for UI
- Workflow routing can be explicit via `workflowKey`/`workflowKeys` on events; otherwise routes by `eventType`.
- Group dimensions are stored as JSON; hash provided in aggregates for grouping.
- All times are UTC; display local with UTC toggle.

## Usage Notes
- Send UTC timestamps; backend stores in UTC.
- `workflowKey` on events routes directly; if absent, current engine skips (UI should enforce mapping by config).
- Group dimensions are stored as JSON; hashes for aggregates may be provided later.
