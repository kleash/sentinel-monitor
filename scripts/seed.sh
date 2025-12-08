#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_URL="${API_URL:-http://localhost:8080}"
AUTH_TOKEN="${AUTH_TOKEN:-}"

AUTH_ARGS=()
if [ -n "${AUTH_TOKEN}" ]; then
  AUTH_ARGS+=(-H "Authorization: Bearer ${AUTH_TOKEN}")
fi

log() {
  echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] $*"
}

iso_now() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

iso_minutes_ago() {
  local mins="$1"
  date -u -v-"${mins}"M +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "-${mins} minutes" +%Y-%m-%dT%H:%M:%SZ
}

post_json() {
  local path="$1"
  local body="$2"
  curl -sf -X POST "${API_URL}${path}" \
    -H "Content-Type: application/json" \
    "${AUTH_ARGS[@]}" \
    -d "${body}"
}

seed_workflows() {
  log "Seeding workflows (trade + equity)..."
  post_json "/workflows" '{
    "name": "Trade Lifecycle",
    "key": "trade-lifecycle",
    "createdBy": "demo",
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
  }' || log "trade-lifecycle already exists, continuing."

  post_json "/workflows" '{
    "name":"Equity Allocation",
    "key":"equity-flow",
    "createdBy":"demo",
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
  }' || log "equity-flow already exists, continuing."
}

seed_trade_events() {
  log "Seeding trade lifecycle events..."
  local tr123_ingest tr123_sys2a tr123_sys2b tr123_sys3 tr123_sys4 tr456_ingest tr456_sys2 tr789_ingest
  tr123_ingest="$(iso_minutes_ago 18)"
  tr123_sys2a="$(iso_minutes_ago 15)"
  tr123_sys2b="$(iso_minutes_ago 13)"
  tr123_sys3="$(iso_minutes_ago 10)"
  tr123_sys4="$(iso_minutes_ago 8)"
  tr456_ingest="$(iso_minutes_ago 4)"
  tr456_sys2="$(iso_minutes_ago 2)"
  tr789_ingest="$(iso_minutes_ago 30)"

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr123-ingest",
  "sourceSystem":"sys1",
  "eventType":"TRADE_INGEST",
  "eventTime":"${tr123_ingest}",
  "correlationKey":"TR123",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"NY"},
  "payload":{"notional":1000000}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr123-sys2-a",
  "sourceSystem":"sys2",
  "eventType":"SYS2_VERIFIED",
  "eventTime":"${tr123_sys2a}",
  "correlationKey":"TR123",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr123-sys2-b",
  "sourceSystem":"sys2",
  "eventType":"SYS2_VERIFIED",
  "eventTime":"${tr123_sys2b}",
  "correlationKey":"TR123",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr123-sys3",
  "sourceSystem":"sys3",
  "eventType":"SYS3_ACK",
  "eventTime":"${tr123_sys3}",
  "correlationKey":"TR123",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr123-sys4",
  "sourceSystem":"sys4",
  "eventType":"SYS4_SETTLED",
  "eventTime":"${tr123_sys4}",
  "correlationKey":"TR123",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr456-ingest",
  "sourceSystem":"sys1",
  "eventType":"TRADE_INGEST",
  "eventTime":"${tr456_ingest}",
  "correlationKey":"TR456",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"LN"},
  "payload":{"notional":500000}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr456-sys2",
  "sourceSystem":"sys2",
  "eventType":"SYS2_VERIFIED",
  "eventTime":"${tr456_sys2}",
  "correlationKey":"TR456",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"LN"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"trade-tr789-ingest",
  "sourceSystem":"sys1",
  "eventType":"TRADE_INGEST",
  "eventTime":"${tr789_ingest}",
  "correlationKey":"TR789",
  "workflowKey":"trade-lifecycle",
  "group":{"book":"EQD","region":"NY"},
  "payload":{"notional":2500000}
}
EOF
  )" || true
}

seed_equity_events() {
  log "Seeding equity lifecycle events..."
  local eq555_recv eq555_validated eq555_booked eq556_recv eq557_recv eq557_validated
  eq555_recv="$(iso_minutes_ago 16)"
  eq555_validated="$(iso_minutes_ago 13)"
  eq555_booked="$(iso_minutes_ago 9)"
  eq556_recv="$(iso_minutes_ago 6)"
  eq557_recv="$(iso_minutes_ago 26)"
  eq557_validated="$(iso_minutes_ago 12)"

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"eq-555-alloc",
  "sourceSystem":"alloc-sys",
  "eventType":"EQUITY_ALLOC",
  "eventTime":"${eq555_recv}",
  "correlationKey":"EQ-555",
  "workflowKey":"equity-flow",
  "group":{"desk":"EQD","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"eq-555-validated",
  "sourceSystem":"alloc-sys",
  "eventType":"EQUITY_VALIDATED",
  "eventTime":"${eq555_validated}",
  "correlationKey":"EQ-555",
  "workflowKey":"equity-flow",
  "group":{"desk":"EQD","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"eq-555-booked",
  "sourceSystem":"alloc-sys",
  "eventType":"EQUITY_BOOKED",
  "eventTime":"${eq555_booked}",
  "correlationKey":"EQ-555",
  "workflowKey":"equity-flow",
  "group":{"desk":"EQD","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"eq-556-alloc",
  "sourceSystem":"alloc-sys",
  "eventType":"EQUITY_ALLOC",
  "eventTime":"${eq556_recv}",
  "correlationKey":"EQ-556",
  "workflowKey":"equity-flow",
  "group":{"desk":"EQD","region":"LN"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"eq-557-alloc",
  "sourceSystem":"alloc-sys",
  "eventType":"EQUITY_ALLOC",
  "eventTime":"${eq557_recv}",
  "correlationKey":"EQ-557",
  "workflowKey":"equity-flow",
  "group":{"desk":"ETF","region":"NY"}
}
EOF
  )" || true

  post_json "/ingest" "$(cat <<EOF
{
  "eventId":"eq-557-validated",
  "sourceSystem":"alloc-sys",
  "eventType":"EQUITY_VALIDATED",
  "eventTime":"${eq557_validated}",
  "correlationKey":"EQ-557",
  "workflowKey":"equity-flow",
  "group":{"desk":"ETF","region":"NY"}
}
EOF
  )" || true
}

print_workflow_ids() {
  local python_cmd="python3"
  if ! command -v python3 >/dev/null 2>&1; then
    if command -v python >/dev/null 2>&1; then
      python_cmd="python"
    else
      log "Python not available; skipping workflow ID summary."
      return
    fi
  fi
  local workflows_json
  workflows_json="$(curl -sf "${AUTH_ARGS[@]}" "${API_URL}/workflows")" || return
  if [ -z "${workflows_json}" ]; then
    log "Workflows endpoint returned empty payload; skipping ID summary."
    return
  fi
  log "Active workflows (key -> id):"
  ${python_cmd} - "${workflows_json}" <<'PY'
import json, sys
payload = sys.argv[1]
data = json.loads(payload)
for wf in data:
    print(f" - {wf.get('key')} -> {wf.get('id')}")
PY
}

log "Using API ${API_URL}"
seed_workflows
seed_trade_events
seed_equity_events
print_workflow_ids
log "Seed complete."
