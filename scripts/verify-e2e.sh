#!/bin/sh
set -eu

# Runs the deterministic Phase 9 Playwright acceptance suite against a disposable
# infrastructure stack. The application, model stub and Vite server are managed by
# Playwright; MySQL/Redis/RabbitMQ/Qdrant are isolated by project name and ports.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/docker-compose.e2e.yml"
E2E_PROJECT="knowflow-e2e"
E2E_STORAGE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/knowflow-e2e.XXXXXX")"

cleanup() {
  docker compose -p "$E2E_PROJECT" -f "$COMPOSE_FILE" down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$E2E_STORAGE_ROOT"
}

trap cleanup EXIT HUP INT TERM

echo "========================================="
echo " KnowFlow verify-e2e"
echo "========================================="

# Never reuse a prior E2E database or queue. This project is dedicated and does not
# share names, ports or volumes with the developer stack in docker-compose.yml.
docker compose -p "$E2E_PROJECT" -f "$COMPOSE_FILE" down -v --remove-orphans
docker compose -p "$E2E_PROJECT" -f "$COMPOSE_FILE" up -d --wait

if [ -z "${KNOWFLOW_SECURITY_MASTER_KEY:-}" ]; then
  KNOWFLOW_SECURITY_MASTER_KEY="$(openssl rand -base64 32)"
  export KNOWFLOW_SECURITY_MASTER_KEY
fi
KNOWFLOW_E2E_STORAGE_ROOT="$E2E_STORAGE_ROOT/files"
export KNOWFLOW_E2E_STORAGE_ROOT

npm --prefix "$REPO_ROOT/frontend" run e2e -- "$@"

echo ""
echo "========================================="
echo " verify-e2e: ALL PASSED"
echo "========================================="
