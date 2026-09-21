#!/usr/bin/env bash
set -euo pipefail

API_URL="${API_URL:-http://localhost:8080}"
API_KEY="${IOTESTE_API_KEY:?Debe definir IOTESTE_API_KEY}"

echo "=== IoTEste EcoWarm - Ejemplo API REST ==="

echo
echo "1. Listar habitaciones"
curl -sS \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/rooms"

echo
echo
echo "2. Consultar room1"
curl -sS \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/rooms/room1"

echo
echo
echo "3. Consultar estado del controlador"
curl -sS \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/controller/status"

echo
echo
echo "4. Validar configuración de habitaciones"
curl -sS \
  -X POST \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/rooms/validate"

echo
echo
echo "=== Fin del ejemplo ==="
