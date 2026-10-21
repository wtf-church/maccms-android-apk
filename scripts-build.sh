#!/usr/bin/env bash
# 在 Docker 中构建手机端 APK
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
MSYS_NO_PATHCONV=1 docker run --rm \
  -v "$ROOT:/workspace" \
  -w /workspace \
  maccms-mobile-build \
  -lc "./gradlew :app:assembleDebug --no-daemon --console=plain"
