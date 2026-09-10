#!/usr/bin/env bash
# Verifies `raw` (DngTiffReader) — fully Android-free, so it's fully
# testable here, unlike camera/AndroidCameraDevice.kt.
#
# Same jar/tooling requirements as scripts/verify-core-jvm.sh.
set -euo pipefail

KOTLINC="${KOTLINC:-kotlinc}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
JARS="/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar"
KOTLIN_STDLIB="$(dirname "$(command -v "$KOTLINC")")/../lib/kotlin-stdlib.jar"

MODEL_SOURCES=$(find "$ROOT"/core/src/main/kotlin/com/astra/core/model -name "*.kt" ! -name "CameraDevice.kt")

echo "== Compiling main sources (core/model + raw reader) =="
"$KOTLINC" -cp "$JARS" \
  $MODEL_SOURCES \
  "$ROOT"/raw/src/main/kotlin/com/astra/raw/DngTiffReader.kt \
  -d "$OUT/main"

echo "== Compiling tests =="
"$KOTLINC" -cp "$OUT/main:$JARS" \
  "$ROOT"/raw/src/test/kotlin/com/astra/raw/DngTiffReaderTest.kt \
  -d "$OUT/test"

echo "== Running tests =="
java -cp "$OUT/main:$OUT/test:$JARS:$KOTLIN_STDLIB" org.junit.runner.JUnitCore \
  com.astra.raw.DngTiffReaderTest

rm -rf "$OUT"
