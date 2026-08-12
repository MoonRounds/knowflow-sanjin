#!/bin/sh
set -eu

# KnowFlow — complete verification
# Calls every default deterministic V1 verification source. Real cloud-model
# smoke/eval remains explicit and is not a PR hard gate.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "========================================="
echo " KnowFlow verify-all"
echo "========================================="

sh "$REPO_ROOT/scripts/verify-fast.sh"
sh "$REPO_ROOT/scripts/check-tracked-secrets.sh"
sh "$REPO_ROOT/scripts/verify-integration.sh"
sh "$REPO_ROOT/scripts/verify-e2e.sh"

echo ""
echo "========================================="
echo " verify-all: ALL PASSED"
echo "========================================="
