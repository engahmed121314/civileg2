#!/usr/bin/env bash
set -x
APK=$(ls app/build/outputs/apk/free/debug/*.apk | head -1)
adb install -r "$APK"
mkdir -p launch-out
aapt2 dump badging "$APK" 2>/dev/null | grep -E "package:|application-label" > launch-out/badging.txt || true
adb shell dumpsys package com.civileg.app.debug | grep -E "versionName|versionCode" > launch-out/version.txt
python3 tools/ci/standalone_launch.py com.civileg.app.debug launch-out
adb logcat -d -t 400 > launch-out/logcat.txt
exit 0
