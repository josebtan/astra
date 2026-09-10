#!/usr/bin/env bash
# Verifies `stacking` (StackingEngine: Mean, Median, Sigma Clip) - pure
# Kotlin arithmetic on LinearImage, zero android.* imports.
#
# Same jar/tooling requirements as scripts/verify-core-jvm.sh.
set -euo pipefail

KOTLINC="${KOTLINC:-kotlinc}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
JARS="/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar"
KOTLIN_STDLIB="$(dirname "$(command -v "$KOTLINC")")/../lib/kotlin-stdlib.jar"

echo "== Compiling main sources (core/model + stacking) =="
"$KOTLINC" -cp "$JARS" \
  "$ROOT"/core/src/main/kotlin/com/astra/core/model/*.kt \
  "$ROOT"/stacking/src/main/kotlin/com/astra/stacking/*.kt \
  -d "$OUT/main"

echo "== Compiling tests =="
"$KOTLINC" -cp "$OUT/main:$JARS" \
  "$ROOT"/stacking/src/test/kotlin/com/astra/stacking/*.kt \
  -d "$OUT/test"

echo "== Running tests =="
java -cp "$OUT/main:$OUT/test:$JARS:$KOTLIN_STDLIB" org.junit.runner.JUnitCore \
  com.astra.stacking.StackingEngineTest

rm -rf "$OUT"
