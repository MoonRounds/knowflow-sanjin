#!/bin/sh
set -eu

# KnowFlow — integration verification script
# Runs Spring Boot and persistence integration tests against MySQL 8.4 Testcontainers.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "========================================="
echo " KnowFlow verify-integration"
echo "========================================="

./knowflow-app/mvnw \
  -s "$REPO_ROOT/knowflow-app/.mvn/settings.xml" \
  -f "$REPO_ROOT/pom.xml" \
  verify

echo ""
echo "========================================="
echo " verify-integration: ALL PASSED"
echo "========================================="
