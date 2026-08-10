#!/bin/sh
set -eu

# Explicit real-provider compatibility smoke. Never part of default CI.
# Usage: sh scripts/live-smoke.sh <provider-label> <model-config-id> [base-url]

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
  echo "Usage: sh scripts/live-smoke.sh <provider-label> <model-config-id> [base-url]" >&2
  exit 2
fi

PROVIDER_LABEL="$1"
CONFIG_ID="$2"
BASE_URL="${3:-http://127.0.0.1:8080}"

case "$CONFIG_ID" in
  ''|0|*[!0-9]*)
    echo "ERROR: model-config-id must be a positive integer string" >&2
    exit 2
    ;;
esac

echo "========================================="
echo " KnowFlow Live Provider Smoke"
echo " Provider: ${PROVIDER_LABEL}"
echo " Config ID: ${CONFIG_ID}"
echo "========================================="

curl -fsS "${BASE_URL}/api/v1/health" >/dev/null

CONFIG_JSON="$(curl -fsS "${BASE_URL}/api/v1/model-configs/${CONFIG_ID}")"
echo "$CONFIG_JSON" | python3 -c '
import json,sys
c=json.load(sys.stdin)
r=c.get("currentRevision") or {}
print("displayName=", c.get("displayName"))
print("providerName=", c.get("providerName"))
print("modelName=", r.get("modelName"))
print("apiKeyMasked=", r.get("apiKeyMasked"))
'

CONNECTION_JSON="$(curl -fsS -X POST "${BASE_URL}/api/v1/model-configs/${CONFIG_ID}/test-connection")"
echo "$CONNECTION_JSON" | python3 -c '
import json,sys
r=json.load(sys.stdin)
if not r.get("success"):
    raise SystemExit("connection/streaming smoke failed: " + str(r.get("message")))
print("connection=PASS")
print("outputTokenCount=", r.get("outputTokenCount"))
print("warnings=", r.get("warnings") or [])
'

UTILITY_JSON="$(curl -fsS -X POST "${BASE_URL}/api/v1/model-configs/${CONFIG_ID}/test-utility-capability")"
echo "$UTILITY_JSON" | python3 -c '
import json,sys
r=json.load(sys.stdin)
if not (r.get("success") and r.get("routerSchemaValid") and r.get("candidateSchemaValid")):
    raise SystemExit("utility smoke failed: " + str(r.get("message")))
print("utility.router=PASS")
print("utility.candidate=PASS")
'

echo "Live smoke PASSED for ${PROVIDER_LABEL}. Record the sanitized result in docs/development/provider-compatibility.md."
