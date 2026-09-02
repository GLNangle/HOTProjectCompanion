#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
classes_dir="$project_dir/build/classes"
stubs_dir="$project_dir/build/stubs"
test_classes_dir="$project_dir/build/test-classes"
manifest_file="$project_dir/build/MANIFEST.MF"

rm -rf "$project_dir/build" "$project_dir/dist"
mkdir -p "$classes_dir" "$stubs_dir" "$test_classes_dir" "$project_dir/dist"

mapfile -t stub_sources < <(find "$project_dir/dev-stubs" -name '*.java' -print | sort)
mapfile -t plugin_sources < <(find "$project_dir/src" -name '*.java' -print | sort)
mapfile -t test_sources < <(find "$project_dir/test" -name '*.java' -print | sort)

java com.sun.tools.javac.Main --release 11 -d "$stubs_dir" "${stub_sources[@]}"
java com.sun.tools.javac.Main --release 11 -cp "$stubs_dir" -d "$classes_dir" "${plugin_sources[@]}"
java com.sun.tools.javac.Main --release 11 -cp "$classes_dir:$stubs_dir" -d "$test_classes_dir" "${test_sources[@]}"
java -cp "$classes_dir:$test_classes_dir:$stubs_dir" org.openstreetmap.josm.plugins.hotprojectcompanion.AllTests
cp -R "$project_dir/resources/." "$classes_dir/"

printf '%s\n' \
  'Manifest-Version: 1.0' \
  'Plugin-Class: org.openstreetmap.josm.plugins.hotprojectcompanion.HotProjectCompanionPlugin' \
  'Plugin-Description: Shows HOT project guidance and provides local building analysis with optional privacy-preserving shared learning.' \
  'Plugin-Version: 1.0.3' \
  'Plugin-Mainversion: 19613' \
  'Plugin-Minimum-Java-Version: 11' \
  'Plugin-Icon: images/dialogs/hotprojectcompanion.svg' \
  'Plugin-Link: https://github.com/GLNangle/HOTProjectCompanion' \
  'Author: Gemma Louise Nangle' \
  > "$manifest_file"

java sun.tools.jar.Main --create --file "$project_dir/dist/hotprojectcompanion.jar" \
  --manifest "$manifest_file" -C "$classes_dir" .

echo "Built $project_dir/dist/hotprojectcompanion.jar"
