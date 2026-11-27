#!/usr/bin/env bash
# Tests the tests: runs task 001's hidden tests against four versions of WeatherFormatter and
# checks that each gets the result it must (see tasks/README.md):
#
#   buggy        the code as reported (no isolation)       → the hidden tests must FAIL
#   hardcoded    a "fix" for the reported sentence only    → must FAIL
#   reference    the fix on main (isolates)                → must PASS
#   alternative  a correct fix, written another way (FSI)  → must PASS
set -euo pipefail

TARGET=app/src/main/kotlin/dev/ahmedabusada/atmos/ui/format/WeatherFormatter.kt
HIDDEN_TEST=app/src/test/kotlin/dev/ahmedabusada/atmos/ui/format/ValueOrderHiddenTest.kt
TASK=tasks/001-value-order
BACKUP=$(mktemp)
cp "$TARGET" "$BACKUP"
trap 'cp "$BACKUP" "$TARGET"; rm -f "$BACKUP" "$HIDDEN_TEST"' EXIT
cp "$TASK/hidden/ValueOrderHiddenTest.kt" "$HIDDEN_TEST"

failures=0
for entry in buggy:fail hardcoded:fail reference:pass alternative:pass; do
    candidate=${entry%%:*}
    expected=${entry##*:}
    cp "$TASK/candidates/$candidate.kt" "$TARGET"
    if ./gradlew ${GRADLE_FLAGS:-} -q :app:testDebugUnitTest --tests "*ValueOrderHiddenTest" \
        > "grade-$candidate.log" 2>&1; then
        actual=pass
    else
        actual=fail
    fi
    if [ "$actual" = "$expected" ]; then
        echo "ok      $candidate: hidden tests $actual, as they must"
    else
        echo "error: $candidate: the hidden tests should $expected but they ${actual}ed"
        grep -E "AssertionError|FAILED|error:|e: " "grade-$candidate.log" | head -15
        failures=$((failures + 1))
    fi
done
rm -f grade-*.log
exit "$failures"
