#!/usr/bin/env bash
# copies each stack's raw Allure results into one folder so a single report can be built
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/reports/allure-results"
rm -rf "$OUT" && mkdir -p "$OUT"
for dir in "$ROOT/typescript/allure-results" "$ROOT/python/allure-results" "$ROOT/java/build/allure-results"; do
  if [ -d "$dir" ]; then cp -R "$dir"/. "$OUT"/; echo "merged $dir"; fi
done
