#!/usr/bin/env bash
# Runs ./gradlew with the given arguments and keeps the full log. When Gradle fails, the useful
# lines (compiler errors, failed tests, lint errors) are published as one workflow annotation,
# so the cause is visible on the PR checks page without opening the raw job log.
set -o pipefail
log="$(mktemp)"

./gradlew "$@" --console=plain 2>&1 | tee "$log"
status=${PIPESTATUS[0]}

if [ "$status" -ne 0 ]; then
  # Every distinct Kotlin compiler error first (paths shortened), then the rest of the interesting lines.
  compile="$(grep -E '^e: ' "$log" | sed 's#file://.*/app/src/##' | awk '!seen[$0]++' | head -40)"
  others="$(grep -E 'FAILED|error:|What went wrong|Caused by|Execution failed|Lint found|: Error:' -A2 "$log" | head -40)"
  details="$(printf '%s\n%s\n' "$compile" "$others" | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
  echo "::error title=Gradle $*::${details}"
  # Lint prints only a summary to the console; the per-issue detail is in the XML report.
  if [ -f app/build/reports/lint-results-debug.xml ]; then
    issues="$(grep -A3 'severity="Error"' app/build/reports/lint-results-debug.xml \
      | head -60 | sed 's/%/%25/g' | awk '{printf "%s%%0A", $0}')"
    echo "::error title=Lint errors::${issues}"
  fi
fi
exit "$status"
