#!/usr/bin/env bash
#
# down.sh
# Detiene y elimina los contenedores de IoTEste EcoWarm.
# Los volúmenes persistentes se conservan.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"

echo "=== Bajando IoTEste EcoWarm ==="

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_FILE}" \
  down

echo
echo "Entorno eliminado. Los volúmenes persistentes fueron conservados."