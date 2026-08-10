#!/bin/sh
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP_OUT="$(mktemp -t knowflow-openapi-types.XXXXXX.ts)"
trap 'rm -f "$TMP_OUT"' EXIT HUP INT TERM

npm --prefix "$REPO_ROOT/frontend" exec -- openapi-typescript \
  "$REPO_ROOT/docs/api/openapi.json" \
  -o "$TMP_OUT"

if ! diff -u "$REPO_ROOT/frontend/src/api/types/generated.ts" "$TMP_OUT"; then
  echo "ERROR: generated API types drifted from docs/api/openapi.json." >&2
  echo "Run: npm --prefix frontend run api:generate" >&2
  exit 1
fi

echo "Generated API types match the OpenAPI snapshot."
