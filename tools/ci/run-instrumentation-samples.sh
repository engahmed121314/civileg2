#!/usr/bin/env bash
# One shell preserves the test status. Collect evidence even when tests fail.
set +e
./gradlew connectedFreeDebugAndroidTest --no-daemon
TEST_EXIT=$?
mkdir -p app/build/drawing-samples
adb pull /data/local/tmp/civileg-drawing-samples/. app/build/drawing-samples/
PULL_EXIT=$?
mkdir -p app/build/toolbox-samples
adb pull /data/local/tmp/civileg-toolbox-samples/. app/build/toolbox-samples/
TOOLBOX_PULL_EXIT=$?
if [ "$TEST_EXIT" -ne 0 ]; then exit "$TEST_EXIT"; fi
if [ "$PULL_EXIT" -ne 0 ]; then exit "$PULL_EXIT"; fi
if [ "$TOOLBOX_PULL_EXIT" -ne 0 ]; then exit "$TOOLBOX_PULL_EXIT"; fi
for tool in calculator converter level ruler; do
  for orientation in portrait landscape; do
    test -s "app/build/toolbox-samples/$tool-$orientation.png" || exit 1
  done
done
test -s app/build/drawing-samples/stair/design-page.png && test -s app/build/drawing-samples/stair/exported-report.pdf
