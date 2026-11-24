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
mkdir -p app/build/toolbox-samples
adb exec-out run-as "$PACKAGE" tar -C files/toolbox-samples -cf - . > app/build/toolbox-samples.tar
TOOLBOX_PULL_EXIT=$?
tar -xf app/build/toolbox-samples.tar -C app/build/toolbox-samples
TOOLBOX_UNPACK_EXIT=$?
if [ "$TEST_EXIT" -ne 0 ]; then exit "$TEST_EXIT"; fi
if [ "$DRAWING_PULL_EXIT" -ne 0 ] || [ "$DRAWING_UNPACK_EXIT" -ne 0 ]; then exit 1; fi
if [ "$TOOLBOX_PULL_EXIT" -ne 0 ] || [ "$TOOLBOX_UNPACK_EXIT" -ne 0 ]; then exit 1; fi
for tool in calculator converter level ruler; do
  for orientation in portrait landscape; do
    test -s "app/build/toolbox-samples/$tool-$orientation.png" || exit 1
  done
done
test -s app/build/drawing-samples/stair/design-page.png && test -s app/build/drawing-samples/stair/exported-report.pdf
