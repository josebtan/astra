#!/usr/bin/env bash
# Verifies the Android-independent parts of `camera` (currently just
# CameraCharacteristicsMapper.kt — mapToCapabilities()). Everything else
# in `camera` (AndroidCameraDevice.kt) needs a real Camera2 HAL and can
# only be verified via GitHub Actions or on-device, not here.
#
# Same jar/tooling requirements as scripts/verify-core-jvm.sh.
set -euo pipefail

KOTLINC="${KOTLINC:-kotlinc}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
JARS="/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar"
KOTLIN_STDLIB="$(dirname "$(command -v "$KOTLINC")")/../lib/kotlin-stdlib.jar"

MODEL_SOURCES=$(find "$ROOT"/core/src/main/kotlin/com/astra/core/model -name "*.kt" ! -name "CameraDevice.kt")

echo "== Compiling main sources (core/model + camera mapper) =="
"$KOTLINC" -cp "$JARS" \
  $MODEL_SOURCES \
  "$ROOT"/camera/src/main/kotlin/com/astra/camera/android/CameraCharacteristicsMapper.kt \
  -d "$OUT/main"

echo "== Compiling tests =="
"$KOTLINC" -cp "$OUT/main:$JARS" \
  "$ROOT"/camera/src/test/kotlin/com/astra/camera/android/CameraCharacteristicsMapperTest.kt \
  -d "$OUT/test"

echo "== Running tests =="
java -cp "$OUT/main:$OUT/test:$JARS:$KOTLIN_STDLIB" org.junit.runner.JUnitCore \
  com.astra.camera.android.CameraCharacteristicsMapperTest

rm -rf "$OUT"
