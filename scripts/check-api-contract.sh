#!/bin/sh
set -eu

# KnowFlow — OpenAPI contract check
# Exports the live OpenAPI JSON and diffs it against the checked-in snapshot.
#
# Usage: sh scripts/check-api-contract.sh [BASE_URL]
#   BASE_URL: running backend origin (default http://127.0.0.1:8080)

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${1:-http://127.0.0.1:8080}"
SNAPSHOT="$REPO_ROOT/docs/api/openapi.json"
TMP_OUT="$(mktemp -t knowflow-openapi.XXXXXX.json)"
TMP_NORM="${TMP_OUT}.norm"
TMP_SNAPSHOT="${TMP_OUT}.snapshot"
trap 'rm -f "$TMP_OUT" "$TMP_NORM" "$TMP_SNAPSHOT"' EXIT HUP INT TERM

echo "========================================="
echo " KnowFlow API contract check"
echo "========================================="

echo "Fetching OpenAPI from ${BASE_URL}/v3/api-docs ..."
if ! curl -fsS "${BASE_URL}/v3/api-docs" -o "$TMP_OUT"; then
  echo "ERROR: could not fetch ${BASE_URL}/v3/api-docs (is the backend running?)" >&2
  exit 1
fi

# Normalize key ordering and remove the runtime-dependent server origin.
python3 -c 'import json,sys; value=json.load(open(sys.argv[1])); value.pop("servers", None); json.dump(value,sys.stdout,indent=4,sort_keys=True); print()' "$TMP_OUT" > "$TMP_NORM"
mv "$TMP_NORM" "$TMP_OUT"
python3 -c 'import json,sys; value=json.load(open(sys.argv[1])); value.pop("servers", None); json.dump(value,sys.stdout,indent=4,sort_keys=True); print()' "$SNAPSHOT" > "$TMP_SNAPSHOT"

if ! diff -u "$TMP_SNAPSHOT" "$TMP_OUT"; then
  echo ""
  echo "ERROR: API contract drifted. Update the checked-in snapshot when the change is intentional:" >&2
  echo "  normalize the fetched JSON (remove servers, sort keys) into docs/api/openapi.json" >&2
  exit 1
fi

echo ""
echo " contract check: PASSED (no drift)"
echo "========================================="
