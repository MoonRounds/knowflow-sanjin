#!/bin/sh
set -eu

# KnowFlow — fast verification script
# Runs: backend unit tests/format-check, frontend typecheck/test/lint/format/build
# Corresponds to: scripts/verify-fast.sh
#
# Usage: sh scripts/verify-fast.sh

# Resolve repository root regardless of where the script is called from
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "========================================="
echo " KnowFlow verify-fast"
echo "========================================="

# ----- Backend -----
echo ""
echo "--- Backend: compile + test + format-check ---"
./knowflow-app/mvnw \
  -s "$REPO_ROOT/knowflow-app/.mvn/settings.xml" \
  -f "$REPO_ROOT/pom.xml" \
  test spotless:check

# ----- Frontend -----
echo ""
echo "--- Frontend: OpenAPI generated type drift ---"
sh "$REPO_ROOT/scripts/check-generated-api-types.sh"

echo ""
echo "--- Frontend: typecheck ---"
npm --prefix "$REPO_ROOT/frontend" run typecheck

echo ""
echo "--- Frontend: unit tests ---"
npm --prefix "$REPO_ROOT/frontend" run test

echo ""
echo "--- Frontend: lint ---"
npm --prefix "$REPO_ROOT/frontend" run lint

echo ""
echo "--- Frontend: format check ---"
npm --prefix "$REPO_ROOT/frontend" run format:check

echo ""
echo "--- Frontend: production build ---"
npm --prefix "$REPO_ROOT/frontend" run build

echo ""
echo "========================================="
echo " verify-fast: ALL PASSED"
echo "========================================="
