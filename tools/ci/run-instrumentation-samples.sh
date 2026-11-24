#!/usr/bin/env bash
# Preserve instrumentation status and extract debug-app-owned files directly.
set +e
./gradlew connectedFreeDebugAndroidTest --no-daemon
TEST_EXIT=$?
PACKAGE=com.civileg.app.debug
adb shell run-as "$PACKAGE" id || exit 1
mkdir -p app/build/drawing-samples
adb exec-out run-as "$PACKAGE" tar -C files/drawing-samples -cf - . > app/build/drawing-samples.tar
DRAWING_PULL_EXIT=$?
tar -xf app/build/drawing-samples.tar -C app/build/drawing-samples
DRAWING_UNPACK_EXIT=$?
if [ "$TEST_EXIT" -ne 0 ]; then exit "$TEST_EXIT"; fi
if [ "$DRAWING_PULL_EXIT" -ne 0 ] || [ "$DRAWING_UNPACK_EXIT" -ne 0 ]; then exit 1; fi
test -s app/build/drawing-samples/stair/design-page.png && test -s app/build/drawing-samples/stair/exported-report.pdf
