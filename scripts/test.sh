#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

MOUNT_DIR="$PROJECT_DIR"

if command -v cygpath >/dev/null 2>&1; then
    MOUNT_DIR="$(cygpath -m "$PROJECT_DIR")"
fi

export MSYS_NO_PATHCONV=1

docker run --rm \
    --mount "type=bind,source=${MOUNT_DIR},target=/workspace" \
    --workdir /workspace \
    maven:3.9-eclipse-temurin-25 \
    mvn -B test