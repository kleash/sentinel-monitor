#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-4300}"
API_URL="${API_URL:-http://localhost:${BACKEND_PORT}}"
SECURITY_DISABLE_AUTH="${SECURITY_DISABLE_AUTH:-true}"
RESET_STATE="${RESET_STATE:-true}"
SKIP_BUILD="${SKIP_BUILD:-false}"
FRONTEND_START_CMD="${FRONTEND_START_CMD:-npm run start:demo -- --port ${FRONTEND_PORT}}"

log() {
  echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] $*"
}

build_backend() {
  if [ "${SKIP_BUILD}" = "true" ]; then
    log "Skipping backend build (SKIP_BUILD=true)."
    return
  fi
  log "Building backend jar (platform-service)..."
  local mvn_cmd="./mvnw"
  if [ ! -x "${ROOT_DIR}/backend/platform-service/mvnw" ]; then
    mvn_cmd="mvn"
  fi
  (cd "${ROOT_DIR}/backend/platform-service" && ${mvn_cmd} -q -DskipTests package)
}

reset_infra() {
  if [ "${RESET_STATE}" = "true" ]; then
    log "Resetting running processes and infra (docker compose down -v)..."
    bash "${ROOT_DIR}/scripts/teardown.sh"
  else
    log "Skipping infra reset (RESET_STATE=false)."
    bash "${ROOT_DIR}/scripts/stop.sh" >/dev/null 2>&1 || true
  fi
}

start_stack() {
  log "Starting demo stack (backend:${BACKEND_PORT}, frontend:${FRONTEND_PORT})..."
  BACKEND_PORT="${BACKEND_PORT}" \
  FRONTEND_PORT="${FRONTEND_PORT}" \
  FRONTEND_START_CMD="${FRONTEND_START_CMD}" \
  SECURITY_DISABLE_AUTH="${SECURITY_DISABLE_AUTH}" \
  API_PROXY_TARGET="http://localhost:${BACKEND_PORT}" \
  bash "${ROOT_DIR}/scripts/start.sh"
}

seed_data() {
  log "Seeding demo workflows/events against ${API_URL} ..."
  API_URL="${API_URL}" bash "${ROOT_DIR}/scripts/seed.sh"
}

verify_endpoints() {
  log "Verifying backend health..."
  curl -sf "${API_URL}/actuator/health" >/dev/null && log "Backend health OK."
  log "Verifying wallboard API..."
  curl -sf "${API_URL}/wallboard?limit=5" >/dev/null && log "Wallboard API reachable."
}

build_backend
reset_infra
start_stack
seed_data
log "Waiting for Kafka consumers and scheduler to process events..."
sleep 18
verify_endpoints
log "Demo stack ready. UI: http://localhost:${FRONTEND_PORT} (demo mode, live backend)"
