#!/usr/bin/env bash
#
# receive-temp.sh
# Se suscribe a los eventos MQTT de los termostatos simulados.
#
# Uso:
#   ./receive-temp.sh
#
set -euo pipefail

CONTAINER_NAME="ioteste-mosquitto"
BROKER_HOST="localhost"
BROKER_PORT="1883"
TOPIC="+/status/#"

echo "Suscrito mediante MQTT a:"
echo "Topic: ${TOPIC}"
echo "Esperando mensajes... (Ctrl+C para salir)"
echo

docker exec -i "${CONTAINER_NAME}" \
  mosquitto_sub \
  -h "${BROKER_HOST}" \
  -p "${BROKER_PORT}" \
  -t "${TOPIC}" \
  -v