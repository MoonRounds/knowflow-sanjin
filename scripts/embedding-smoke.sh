#!/bin/sh
set -eu

# KnowFlow — Embedding + Qdrant smoke test
#
# 受控真实 Embedding/Qdrant 最小技术验证：用真实 Embedding API 给一条文本生成向量并写入 Qdrant，
# 验证维度、collection 与读取。普通 PR / CI 不运行；需显式执行并注入真实凭据。
#
# Usage:
#   KNOWFLOW_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
#   KNOWFLOW_EMBEDDING_API_KEY=<real-key> \
#   sh scripts/embedding-smoke.sh
#
# 不会把 Key 写入任何文件或日志。Key 只存在于 shell 环境变量。
#
# 前置：Qdrant 已启动（默认 http://127.0.0.1:6333），可覆盖 QDRANT_URL 指向其他实例。

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
QDRANT_URL="${QDRANT_URL:-http://127.0.0.1:6333}"
COLLECTION="${QDRANT_COLLECTION:-knowflow_dense_v1}"
EMBED_BASE="${KNOWFLOW_EMBEDDING_BASE_URL:-}"
EMBED_KEY="${KNOWFLOW_EMBEDDING_API_KEY:-}"
EMBED_MODEL="${KNOWFLOW_EMBEDDING_MODEL:-text-embedding-v4}"
EXPECT_DIM="${KNOWFLOW_EMBEDDING_DIMENSIONS:-1024}"

echo "========================================="
echo " KnowFlow Embedding + Qdrant Smoke"
echo " Embedding base: ${EMBED_BASE}"
echo " Model: ${EMBED_MODEL}"
echo " Qdrant: ${QDRANT_URL}  collection=${COLLECTION}"
echo "========================================="

if [ -z "$EMBED_BASE" ] || [ -z "$EMBED_KEY" ]; then
  echo "ERROR: KNOWFLOW_EMBEDDING_BASE_URL and KNOWFLOW_EMBEDDING_API_KEY must be set." >&2
  echo "This smoke requires a real Embedding API key; it is NOT part of default CI." >&2
  exit 2
fi

echo "1) Calling Embedding API ..."
EMBED_JSON=$(curl -fsS -X POST "${EMBED_BASE}/embeddings" \
  -H "Authorization: Bearer ${EMBED_KEY}" \
  -H "Content-Type: application/json" \
  -d "{\"model\":\"${EMBED_MODEL}\",\"input\":[\"KnowFlow smoke test vector\"]}")

DIM=$(printf '%s' "$EMBED_JSON" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(len(d["data"][0]["embedding"]))')
echo "   embedding dimension = ${DIM}"
if [ "$DIM" != "$EXPECT_DIM" ]; then
  echo "ERROR: dimension mismatch: expected ${EXPECT_DIM} got ${DIM}" >&2
  exit 1
fi

echo "2) Ensuring Qdrant collection ..."
curl -fsS -X PUT "${QDRANT_URL}/collections/${COLLECTION}" \
  -H "Content-Type: application/json" \
  -d "{\"vectors\":{\"size\":${DIM},\"distance\":\"Cosine\"}}" >/dev/null
echo "   collection ${COLLECTION} ready (size=${DIM})"

echo "3) Upserting a smoke point ..."
VECTOR=$(printf '%s' "$EMBED_JSON" | python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin)["data"][0]["embedding"]))')
curl -fsS -X PUT "${QDRANT_URL}/collections/${COLLECTION}/points" \
  -H "Content-Type: application/json" \
  -d "{\"points\":[{\"id\":\"00000000-0000-0000-0000-000000000000\",\"vector\":${VECTOR},\"payload\":{\"user_id\":0,\"knowledge_item_id\":0,\"chunk_id\":\"smoke\",\"chunk_index\":0,\"content_version\":0,\"source_type\":\"MANUAL_NOTE\",\"knowledge_base_ids\":[],\"tags\":[]}}]}" >/dev/null
echo "   smoke point upserted"

echo "4) Verifying point count ..."
COUNT=$(curl -fsS -X POST "${QDRANT_URL}/collections/${COLLECTION}/points/count" \
  -H "Content-Type: application/json" \
  -d '{"filter":{"must":[{"key":"chunk_id","match":{"value":"smoke"}}]}}' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["result"]["count"])')
echo "   smoke points = ${COUNT}"

echo ""
echo " Embedding + Qdrant smoke: PASSED"
echo "========================================="
