#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API_URL="${API_URL:-http://localhost:8080}"

log() {
  echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] $*"
}

post_json() {
  local path="$1"
  local body="$2"
  curl -sf -X POST "${API_URL}${path}" \
    -H "Content-Type: application/json" \
    -d "${body}"
}

seed_workflows() {
  log "Seeding workflows..."
  post_json "/workflows" '{
    "name": "Trade Lifecycle",
    "key": "trade-lifecycle",
    "createdBy": "seed",
    "graph": {
      "nodes": [
        {"key":"ingest","eventType":"TRADE_INGEST","start":true},
        {"key":"sys2-verify","eventType":"SYS2_VERIFIED"},
        {"key":"sys3-ack","eventType":"SYS3_ACK"},
        {"key":"sys4-settle","eventType":"SYS4_SETTLED","terminal":true}
      ],
      "edges": [
        {"from":"ingest","to":"sys2-verify","maxLatencySec":300,"severity":"amber"},
        {"from":"sys2-verify","to":"sys3-ack","maxLatencySec":300,"severity":"red"},
        {"from":"sys3-ack","to":"sys4-settle","maxLatencySec":900,"severity":"amber"}
      ],
      "groupDimensions":["book","region"]
    },
    "runbookUrl":"https://runbooks/trade"
  }' || true

  post_json "/workflows" '{
    "name": "Daily File Receipt",
    "key": "file-receipt",
    "createdBy": "seed",
    "graph": {
      "nodes": [
        {"key":"file-received","eventType":"FILE_RECEIVED","start":true},
        {"key":"validated","eventType":"FILE_VALIDATED"},
        {"key":"loaded","eventType":"FILE_LOADED","terminal":true}
      ],
      "edges": [
        {"from":"file-received","to":"validated","absoluteDeadline":"08:00Z","severity":"red"},
        {"from":"validated","to":"loaded","maxLatencySec":900,"severity":"amber"}
      ],
      "groupDimensions":["feed","region"]
    },
    "runbookUrl":"https://runbooks/file"
  }' || true
}

seed_events() {
  log "Seeding events for trade lifecycle..."
  post_json "/ingest" '{
    "sourceSystem":"sys1",
    "eventType":"TRADE_INGEST",
    "eventTime":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "correlationKey":"TR123",
    "workflowKey":"trade-lifecycle",
    "group":{"book":"EQD","region":"NY"},
    "payload":{"notional":1000000}
  }' || true

  post_json "/ingest" '{
    "sourceSystem":"sys2",
    "eventType":"SYS2_VERIFIED",
    "eventTime":"'$(date -u -v-2M +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d\"-2 minutes\" +%Y-%m-%dT%H:%M:%SZ)'",
    "correlationKey":"TR123",
    "workflowKey":"trade-lifecycle",
    "group":{"book":"EQD","region":"NY"}
  }' || true

  log "Seeding events for file receipt..."
  post_json "/ingest" '{
    "sourceSystem":"batch",
    "eventType":"FILE_RECEIVED",
    "eventTime":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'",
    "correlationKey":"DAILY_FEED",
    "workflowKey":"file-receipt",
    "group":{"feed":"DAILY_FEED","region":"NY"}
  }' || true
}

log "Using API ${API_URL}"
seed_workflows
seed_events
log "Seed complete."
