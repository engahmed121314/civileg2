#!/usr/bin/env bash
# Preserve instrumentation status and extract debug-app-owned files directly.
set +e
./gradlew connectedFreeDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon
TEST_EXIT=$?
# Read the actual variant application id, never assume a suffix.
PACKAGE=$(python3 -c 'import json; print(json.load(open("app/build/outputs/apk/free/debug/output-metadata.json"))["applicationId"])')
if [ -z "$PACKAGE" ]; then exit 1; fi
adb shell pm list packages | grep -F "$PACKAGE" || exit 1
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
