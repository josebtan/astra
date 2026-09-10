#!/usr/bin/env bash
# Verifies `calibration` (MasterFrameBuilder, DefectMapBuilder,
# DefectCorrector, CalibrationEngine) - all pure Kotlin arithmetic on
# LinearImage, zero android.* imports, fully testable here.
#
# Same jar/tooling requirements as scripts/verify-core-jvm.sh.
set -euo pipefail

KOTLINC="${KOTLINC:-kotlinc}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
JARS="/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar"
KOTLIN_STDLIB="$(dirname "$(command -v "$KOTLINC")")/../lib/kotlin-stdlib.jar"

echo "== Compiling main sources (core/model + calibration) =="
"$KOTLINC" -cp "$JARS" \
  "$ROOT"/core/src/main/kotlin/com/astra/core/model/*.kt \
  "$ROOT"/calibration/src/main/kotlin/com/astra/calibration/*.kt \
  -d "$OUT/main"

echo "== Compiling tests =="
"$KOTLINC" -cp "$OUT/main:$JARS" \
  "$ROOT"/calibration/src/test/kotlin/com/astra/calibration/*.kt \
  -d "$OUT/test"

echo "== Running tests =="
java -cp "$OUT/main:$OUT/test:$JARS:$KOTLIN_STDLIB" org.junit.runner.JUnitCore \
  com.astra.calibration.MasterFrameBuilderTest \
  com.astra.calibration.DefectMapBuilderTest \
  com.astra.calibration.DefectCorrectorTest \
  com.astra.calibration.CalibrationEngineTest

rm -rf "$OUT"
