#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
PID_DIR="${ROOT_DIR}/scripts/.pids"
FRONTEND_PORT="${FRONTEND_PORT:-4300}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
DB_URL="${DB_URL:-jdbc:mariadb://localhost:3306/sentinel}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-sentinel}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:29092}"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

log() {
  echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] $*"
}

start_infra() {
  if command -v docker >/dev/null 2>&1; then
    log "Starting infrastructure (MariaDB, Kafka, Zookeeper)..."
    docker compose -f "${ROOT_DIR}/ops/docker-compose.yml" up -d
  else
    log "Docker not installed; skipping infrastructure start."
  fi
}

start_backend() {
  if [ -f "${PID_DIR}/backend.pid" ] && kill -0 "$(cat "${PID_DIR}/backend.pid")" 2>/dev/null; then
    log "Backend already running (PID $(cat "${PID_DIR}/backend.pid"))."
    return
  fi
  log "Starting backend platform-service on port ${BACKEND_PORT}..."
  nohup DB_URL="${DB_URL}" DB_USER="${DB_USER}" DB_PASSWORD="${DB_PASSWORD}" \
    KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" \
    java -jar "${ROOT_DIR}/backend/platform-service/target/platform-service-0.0.1-SNAPSHOT.jar" \
      --server.port="${BACKEND_PORT}" \
      --spring.profiles.active=local \
      > "${LOG_DIR}/backend.log" 2>&1 &
  echo $! > "${PID_DIR}/backend.pid"
}

start_frontend() {
  if [ -f "${PID_DIR}/frontend.pid" ] && kill -0 "$(cat "${PID_DIR}/frontend.pid")" 2>/dev/null; then
    log "Frontend already running (PID $(cat "${PID_DIR}/frontend.pid"))."
    return
  fi
  log "Installing frontend deps (if needed) and starting Angular dev server on port ${FRONTEND_PORT}..."
  (cd "${ROOT_DIR}/frontend" && npm install >/dev/null)
  nohup bash -c "cd \"${ROOT_DIR}/frontend\" && npm run start:mock -- --port ${FRONTEND_PORT}" \
    > "${LOG_DIR}/frontend.log" 2>&1 &
  echo $! > "${PID_DIR}/frontend.pid"
}

healthcheck() {
  log "Waiting for backend on :${BACKEND_PORT}..."
  for _ in {1..30}; do
    if curl -sf "http://localhost:${BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
      log "Backend is healthy."
      break
    fi
    sleep 2
  done
  log "Waiting for frontend on :${FRONTEND_PORT}..."
  for _ in {1..30}; do
    if curl -sf "http://localhost:${FRONTEND_PORT}" >/dev/null 2>&1; then
      log "Frontend is reachable."
      break
    fi
    sleep 2
  done
}

start_infra
start_backend
start_frontend
healthcheck
log "All services started. Logs in ${LOG_DIR}."
