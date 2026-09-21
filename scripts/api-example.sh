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
echo "3. Consultar histórico de temperaturas de room1"
curl -sS \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/rooms/room1/readings"

echo
echo
echo "4. Validar configuración de habitaciones"
curl -sS \
  -X POST \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/rooms/validate"

echo
echo
echo "5. Consultar estado del controlador"
curl -sS \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/controller/status"

echo
echo
echo "6. Iniciar controlador automático"
curl -sS \
  -X POST \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/controller/start"

echo
echo
echo "7. Consultar estado del controlador"
curl -sS \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/controller/status"

echo
echo
echo "8. Detener controlador automático"
curl -sS \
  -X POST \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/controller/stop"

echo
echo
echo "9. Encender manualmente el switch de room1"
curl -sS \
  -X POST \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/rooms/room1/switch/on"

echo
echo
echo "10. Apagar manualmente el switch de room1"
curl -sS \
  -X POST \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/rooms/room1/switch/off"

echo
echo
echo "11. Estado final del controlador"
curl -sS \
  -H "X-API-Key: ${API_KEY}" \
  "${API_URL}/controller/status"

echo
echo
echo "=== Fin del ejemplo ==="