#!/usr/bin/env bash
#
# send-temp.sh
# Publica un mensaje simulado de temperatura/humedad
# utilizando el cliente MQTT disponible dentro del contenedor Mosquitto.
#
# Uso:
#   ./send-temp.sh
#   ./send-temp.sh 23.5 55
#
set -euo pipefail

CONTAINER_NAME="ioteste-mosquitto"
BROKER_HOST="localhost"
BROKER_PORT="1883"
TOPIC="shellies/shellyht-test/status"

TEMP="${1:-22.0}"
HUM="${2:-50}"

PAYLOAD=$(cat <<PAYLOAD_EOF
{"temperature": ${TEMP}, "humidity": ${HUM}, "battery": 88}
PAYLOAD_EOF
)

echo "Publicando mediante Docker en ${BROKER_HOST}:${BROKER_PORT} topic=${TOPIC}"
echo "Payload: ${PAYLOAD}"

docker exec "${CONTAINER_NAME}" \
  mosquitto_pub \
  -h "${BROKER_HOST}" \
  -p "${BROKER_PORT}" \
  -t "${TOPIC}" \
  -m "${PAYLOAD}"

echo "Mensaje publicado."