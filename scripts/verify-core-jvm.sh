#!/usr/bin/env bash
# Verifies the Android-independent Kotlin sources in `core` (model,
# storage, storage/settings — excluding DataStoreSettingsRepository.kt,
# which needs a real Android runtime).
#
# Requires: kotlinc 1.9.x on PATH (or set $KOTLINC), and these jars
# available on the system (install via `apt-get install <pkg>` on Debian/
# Ubuntu, package name in parentheses):
#   junit4.jar                (junit4)
#   hamcrest-core.jar         (junit4, pulled in as a dependency)
#   kotlinx-coroutines-core.jar (libkotlinx-coroutines-java)
#   atomicfu.jar              (libkotlinx-atomicfu-java)
#
# This does NOT replace `./gradlew test`, which is the real build; it's a
# fast sanity check that doesn't need network access to Google's Maven repo.
set -euo pipefail

KOTLINC="${KOTLINC:-kotlinc}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$(mktemp -d)"
JARS="/usr/share/java/junit4.jar:/usr/share/java/hamcrest-core.jar:/usr/share/java/kotlinx-coroutines-core.jar:/usr/share/java/atomicfu.jar"
KOTLIN_STDLIB="$(dirname "$(command -v "$KOTLINC")")/../lib/kotlin-stdlib.jar"

MODEL_SOURCES=$(find "$ROOT"/core/src/main/kotlin/com/astra/core/model -name "*.kt" ! -name "CameraDevice.kt")

echo "== Compiling main sources =="
"$KOTLINC" -cp "$JARS" \
  $MODEL_SOURCES \
  "$ROOT"/core/src/main/kotlin/com/astra/core/storage/SessionFileStore.kt \
  "$ROOT"/core/src/main/kotlin/com/astra/core/storage/settings/UserSettings.kt \
  "$ROOT"/core/src/main/kotlin/com/astra/core/storage/settings/SettingsRepository.kt \
  "$ROOT"/core/src/main/kotlin/com/astra/core/storage/settings/InMemorySettingsRepository.kt \
  -d "$OUT/main"

echo "== Compiling tests =="
"$KOTLINC" -cp "$OUT/main:$JARS" \
  "$ROOT"/core/src/test/kotlin/com/astra/core/model/ObservationSessionTest.kt \
  "$ROOT"/core/src/test/kotlin/com/astra/core/storage/SessionFileStoreTest.kt \
  "$ROOT"/core/src/test/kotlin/com/astra/core/storage/settings/InMemorySettingsRepositoryTest.kt \
  -d "$OUT/test"

echo "== Running tests =="
java -cp "$OUT/main:$OUT/test:$JARS:$KOTLIN_STDLIB" org.junit.runner.JUnitCore \
  com.astra.core.model.ObservationSessionTest \
  com.astra.core.storage.SessionFileStoreTest \
  com.astra.core.storage.settings.InMemorySettingsRepositoryTest

rm -rf "$OUT"
