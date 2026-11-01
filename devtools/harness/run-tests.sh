#!/usr/bin/env bash
# Runs SableGuard's logic tests without Minecraft: fakes of the Sable/Minecraft classes the core code touches
# (src/), the scenario tests (test/) and the real mod sources listed in mod-sources.txt.
# Needs Gson and NightConfig (core + toml) on the classpath. By default they are searched in the Gradle cache,
# which has them after one `./gradlew build`. Override with: LIBS_CP=/a.jar:/b.jar ./run-tests.sh
set -euo pipefail
cd "$(dirname "$0")"
if [[ -z "${LIBS_CP:-}" ]]; then
  find_jar() { find "$HOME/.gradle/caches" -name "$1" 2>/dev/null | sort -V | tail -1; }
  LIBS_CP="$(find_jar 'gson-2.*.jar'):$(find_jar 'core-3.*.jar'):$(find_jar 'toml-3.*.jar')"
fi
rm -rf out && mkdir out
javac -nowarn -cp "$LIBS_CP" -d out @fakes.txt $(cat mod-sources.txt) ../../src/main/java/dev/sableguard/DuplicateMods.java ../../src/main/java/dev/sableguard/tools/ScalableLuxOverride.java
for t in HarnessTest FixRegistryTest DuplicateTest LuxTest; do
  echo "=== $t"; java -cp "out:$LIBS_CP" "$t"
done
