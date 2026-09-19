#!/usr/bin/env bash
#
# up.sh
# Levanta el broker Mosquitto y el suscriptor Java (construyendo la
# imagen si es necesario) en modo detached.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/../docker/docker-compose.yml"

echo "Levantando entorno IoTEste (Mosquitto + suscriptor Java)..."
docker compose --env-file "${SCRIPT_DIR}/../.env" -f "${COMPOSE_FILE}" up -d --build

echo
echo "Entorno levantado. Servicios activos:"
docker compose --env-file "${SCRIPT_DIR}/../.env" -f "${COMPOSE_FILE}" ps

echo
echo "Para ver los logs del suscriptor Java: docker compose -f docker/docker-compose.yml logs -f subscriber"