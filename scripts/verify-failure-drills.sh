#!/bin/sh
set -eu

# Standalone Phase 9 failure drill: backend Retry/DLQ/recovery semantics plus
# the key frontend failure paths. Full verification invokes the same tests via
# verify-integration.sh and verify-e2e.sh without duplicating this wrapper.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "========================================="
echo " KnowFlow verify-failure-drills"
echo "========================================="

"$REPO_ROOT/knowflow-app/mvnw" \
  -s "$REPO_ROOT/knowflow-app/.mvn/settings.xml" \
  -f "$REPO_ROOT/pom.xml" \
  -pl knowflow-app \
  -Dit.test=IndexTaskConsumerIT,DocumentParseConsumerIT,ExtractionTaskConsumerIT,ProcessingTaskServiceRecoveryIT \
  verify

sh "$REPO_ROOT/scripts/verify-e2e.sh" e2e/failure-paths.spec.ts

echo ""
echo "========================================="
echo " verify-failure-drills: ALL PASSED"
echo "========================================="
