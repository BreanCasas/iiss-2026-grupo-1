#!/usr/bin/env bash
#
# stop.sh
# Detiene los contenedores de IoTEste EcoWarm sin eliminarlos.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"

echo "=== Deteniendo IoTEste EcoWarm ==="

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_FILE}" \
  stop

echo
echo "Contenedores detenidos."