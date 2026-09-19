#!/usr/bin/env bash
#
# receive-temp.sh
# Se suscribe al broker MQTT y muestra por consola todos los
# mensajes publicados bajo el topic "shellies/#".
# El cliente MQTT se ejecuta dentro del contenedor Mosquitto.
#
# Uso:
#   ./receive-temp.sh
#
set -euo pipefail

CONTAINER_NAME="ioteste-mosquitto"
BROKER_HOST="localhost"
BROKER_PORT="1883"
TOPIC="shellies/#"

echo "Suscrito mediante Docker a ${BROKER_HOST}:${BROKER_PORT} topic=${TOPIC}"
echo "Esperando mensajes... (Ctrl+C para salir)"
echo

docker exec -i "${CONTAINER_NAME}" \
  mosquitto_sub \
  -h "${BROKER_HOST}" \
  -p "${BROKER_PORT}" \
  -t "${TOPIC}" \
  -v