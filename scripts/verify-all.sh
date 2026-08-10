#!/bin/sh
set -eu

# KnowFlow — complete verification
# Calls verify-fast.sh and verify-integration.sh

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "========================================="
echo " KnowFlow verify-all"
echo "========================================="

sh "$REPO_ROOT/scripts/verify-fast.sh"
sh "$REPO_ROOT/scripts/verify-integration.sh"

echo ""
echo "========================================="
echo " verify-all: ALL PASSED"
echo "========================================="
