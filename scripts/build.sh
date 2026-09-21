#!/usr/bin/env bash
#
# build.sh
# Compila todos los módulos Java de IoTEste EcoWarm utilizando Docker,
# sin depender de tener Java o Maven instalados en el sistema anfitrión.
#
# Utiliza el Dockerfile multi-stage ubicado en la raíz del repositorio
# y construye cada módulo mediante su target correspondiente.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
DOCKERFILE="${ROOT_DIR}/Dockerfile"

echo "=== Build de IoTEste EcoWarm (vía Docker) ==="

echo ""
echo "--- Compilando módulo: subscriber ---"
docker build \
  -f "${DOCKERFILE}" \
  --target subscriber \
  -t ioteste-subscriber:build \
  "${ROOT_DIR}"

echo ""
echo "--- Compilando módulo: generator ---"
docker build \
  -f "${DOCKERFILE}" \
  --target generator \
  -t ioteste-generator:build \
  "${ROOT_DIR}"

echo ""
echo "--- Compilando módulo: api ---"
docker build \
  -f "${DOCKERFILE}" \
  --target api \
  -t ioteste-api:build \
  "${ROOT_DIR}"

echo ""
echo "--- Compilando módulo: switch-stub ---"
docker build \
  -f "${DOCKERFILE}" \
  --target switch-stub \
  -t ioteste-switch-stub:build \
  "${ROOT_DIR}"

echo ""
echo "=== Build completo. Todos los módulos compilaron correctamente. ==="