#!/usr/bin/env bash
#
# up.sh
# Construye y levanta el entorno completo de IoTEste EcoWarm.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"

echo "=== Levantando IoTEste EcoWarm ==="

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_FILE}" \
  up -d --build

echo
echo "=== Servicios activos ==="

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_FILE}" \
  ps

echo
echo "Para ver los logs:"
echo "docker compose --env-file .env -f docker/docker-compose.yml logs -f"