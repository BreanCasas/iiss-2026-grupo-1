#!/usr/bin/env bash
#
# send-temp.sh
# Publica manualmente una lectura de temperatura simulada mediante MQTT.
#
# Uso:
#   ./send-temp.sh
#   ./send-temp.sh 23.5
#
set -euo pipefail

CONTAINER_NAME="ioteste-mosquitto"
BROKER_HOST="localhost"
BROKER_PORT="1883"
TOPIC="ht-sim-room1/status/temperature:0"

TEMP="${1:-22.0}"

TEMP_F=$(LC_ALL=C awk "BEGIN { printf \"%.1f\", (${TEMP} * 9 / 5) + 32 }")
TS=$(( $(date +%s) * 1000 ))

PAYLOAD=$(cat <<PAYLOAD_EOF
{"ts":${TS},"tC":${TEMP},"id":0,"tF":${TEMP_F}}
PAYLOAD_EOF
)

echo "Publicando mediante MQTT:"
echo "Topic: ${TOPIC}"
echo "Payload: ${PAYLOAD}"

docker exec "${CONTAINER_NAME}" \
  mosquitto_pub \
  -h "${BROKER_HOST}" \
  -p "${BROKER_PORT}" \
  -t "${TOPIC}" \
  -m "${PAYLOAD}"

echo "Mensaje publicado."