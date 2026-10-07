#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

if [[ -f .env ]]; then
    set -a
    source .env
    set +a
fi

: "${IOTESTE_API_KEY:?Definir IOTESTE_API_KEY en .env}"

API_URL="${IOTESTE_API_URL:-http://localhost:8080}"
API_URL="${API_URL%/}"

echo "1. Consultar estado del control"
curl --fail-with-body --silent --show-error \
    -H "X-API-Key: ${IOTESTE_API_KEY}" \
    "${API_URL}/control"
echo

echo "2. Consultar inventario del sitio"
curl --fail-with-body --silent --show-error \
    -H "X-API-Key: ${IOTESTE_API_KEY}" \
    "${API_URL}/sitio"
echo