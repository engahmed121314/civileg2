#!/usr/bin/env bash
# One shell preserves the test status. Collect evidence even when tests fail.
set +e
./gradlew connectedFreeDebugAndroidTest --no-daemon
TEST_EXIT=$?
mkdir -p app/build/drawing-samples
adb pull /data/local/tmp/civileg-drawing-samples/. app/build/drawing-samples/
PULL_EXIT=$?
if [ "$TEST_EXIT" -ne 0 ]; then exit "$TEST_EXIT"; fi
if [ "$PULL_EXIT" -ne 0 ]; then exit "$PULL_EXIT"; fi
test -s app/build/drawing-samples/stair/design-page.png && test -s app/build/drawing-samples/stair/exported-report.pdf
