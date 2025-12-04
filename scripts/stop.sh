#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="${ROOT_DIR}/scripts/.pids"

log() {
  echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] $*"
}

stop_proc() {
  local name="$1"
  local pid_file="${PID_DIR}/${name}.pid"
  if [ -f "${pid_file}" ]; then
    local pid
    pid=$(cat "${pid_file}")
    if kill -0 "${pid}" 2>/dev/null; then
      log "Stopping ${name} (PID ${pid})..."
      kill "${pid}" || true
    fi
    rm -f "${pid_file}"
  else
    log "No PID file for ${name}, skipping."
  fi
}

stop_proc backend
stop_proc frontend

if command -v docker >/dev/null 2>&1; then
  log "Stopping infrastructure containers (keeping volumes)..."
  docker compose -f "${ROOT_DIR}/ops/docker-compose.yml" stop
fi

log "Stopped."
