#!/usr/bin/env bash
# Verifies every jar in a directory is a valid Minecraft plugin jar:
#   - Paper:    paper-plugin.yml (or plugin.yml)
#   - Velocity: velocity-plugin.json
# and, when a version is given, that the file name carries it.
# Usage: scripts/verify-plugin-jars.sh [dir=build/dist] [expected-version]
set -euo pipefail

dir="${1:-build/dist}"
version="${2:-}"
shopt -s nullglob
jars=("$dir"/*.jar)

if [ ${#jars[@]} -eq 0 ]; then
  echo "::error::No plugin jars found in $dir"
  exit 1
fi

fail=0
for jar in "${jars[@]}"; do
  name="$(basename "$jar")"
  entries="$(unzip -Z1 "$jar")" || { echo "::error::$name is not a valid zip/jar"; fail=1; continue; }

  if [[ "$name" != klrnbk-* ]]; then
    echo "::error::$name does not start with klrnbk-"; fail=1
  fi
  if [ -n "$version" ] && [[ "$name" != *"-$version.jar" ]]; then
    echo "::error::$name does not end with -$version.jar"; fail=1
  fi

  if grep -qxE 'paper-plugin\.yml|plugin\.yml' <<<"$entries"; then
    platform=paper
    desc="$(unzip -p "$jar" paper-plugin.yml 2>/dev/null || unzip -p "$jar" plugin.yml)"
    if [ -n "$version" ] && ! grep -qE "^version:\s*['\"]?$version['\"]?\s*$" <<<"$desc"; then
      echo "::error::$name: descriptor version does not match $version"; fail=1
    fi
  elif grep -qx 'velocity-plugin.json' <<<"$entries"; then
    platform=velocity
  else
    echo "::error::$name has no paper-plugin.yml / plugin.yml / velocity-plugin.json"
    fail=1; continue
  fi
  echo "ok  $name ($platform)"
done
exit $fail
