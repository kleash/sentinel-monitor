#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

bash "${ROOT_DIR}/scripts/stop.sh"

if command -v docker >/dev/null 2>&1; then
  echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] Removing infrastructure containers and volumes..."
  docker compose -f "${ROOT_DIR}/ops/docker-compose.yml" down -v
fi

echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] Teardown complete."
