#!/usr/bin/env bash
# Bounded real-launch evidence for Splash Treatment B.
# Resolves the installed MAIN/LAUNCHER component via the package manager,
# verifies it is the intended SplashActivity, force-stops the disposable app
# for a grounded cold start, then captures the actual display at target
# offsets after the am start REQUEST, recording the real monotonic elapsed
# of every sample (begin/end) relative to both request and return.
# A short on-device screenrecord runs from just before the launch through
# past the observed transition window. No taps, no input, no consent/ad/
# navigation interaction; whatever the app shows is captured as-is.
# Diagnostic only; it never gates CI, and it exits nonzero when the launch
# or every capture failed - no success claims. Whether the video's frames
# advance where the stills froze is UNPROVEN until the actual MP4 and its
# extracted frames are inspected; this script records validity and
# provenance only, never a composition claim.
set -u
OUT=splash-launch-evidence
mkdir -p "$OUT"
PKG=com.civileg.app.debug
APK=app/build/outputs/apk/free/debug/app-free-debug.apk
STATUS="$OUT/status.txt"
TIMELINE="$OUT/timeline.txt"
REC_DEVICE=/sdcard/splash-launch-recording.mp4
REC_LIMIT_S=15
: > "$STATUS"; : > "$TIMELINE"

now_ms() { date +%s%3N; }

# Collects the recording in either outcome: waits for screenrecord to end on
# its own fixed limit, pulls BEFORE any cleanup, and records honest validity
# (exit codes, size, ftyp signature, sha256). Never a composition claim.
collect_recording() {
  wait "$REC_PID"
  REC_RC=$?
  adb pull "$REC_DEVICE" "$OUT/splash-launch-recording.mp4" > "$OUT/screenrecord-pull.txt" 2>&1
  PULL_RC=$?
  REC_SIZE=$(stat -c%s "$OUT/splash-launch-recording.mp4" 2>/dev/null || echo 0)
  REC_MAGIC=$(head -c8 "$OUT/splash-launch-recording.mp4" 2>/dev/null | od -An -c | tr -s ' ')
  {
    echo "screenrecord_wait_exit=$REC_RC pull_exit=$PULL_RC bytes=$REC_SIZE"
    echo "screenrecord_header=$REC_MAGIC"
    if [ -s "$OUT/splash-launch-recording.mp4" ]; then
      echo -n "recording_sha256="; sha256sum "$OUT/splash-launch-recording.mp4" | cut -d' ' -f1
    else
      echo "recording_sha256=none (empty or missing file)"
    fi
  } >> "$STATUS"
}

# --- Context: build head, APK digest, package version, geometry, bars ------
{
  echo "git_head=$(git rev-parse HEAD)"
  echo -n "apk_sha256="; sha256sum "$APK" | cut -d' ' -f1
  echo "sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "device=$(adb shell getprop ro.product.model | tr -d '\r')"
  echo "wm_size=$(adb shell wm size | tr -d '\r')"
  echo "wm_density=$(adb shell wm density | tr -d '\r')"
  echo "versionName=$(adb shell dumpsys package "$PKG" | grep -m1 versionName | tr -d '\r')"
  echo "versionCode=$(adb shell dumpsys package "$PKG" | grep -m1 versionCode | tr -d '\r')"
  adb shell dumpsys window | grep -E "mStatusBar|mNavigationBar|isStatusBarVisible|isNavBarVisible" | head -6
} > "$OUT/context.txt" 2>&1

# --- Animation scales (must stay at system defaults: sweep IS the evidence)
{
  echo "window_animation_scale=$(adb shell settings get global window_animation_scale | tr -d '\r')"
  echo "transition_animation_scale=$(adb shell settings get global transition_animation_scale | tr -d '\r')"
  echo "animator_duration_scale=$(adb shell settings get global animator_duration_scale | tr -d '\r')"
} > "$OUT/animation-scales.txt" 2>&1

# --- 1. Resolve the installed MAIN/LAUNCHER via PM, verify SplashActivity --
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PKG" > "$OUT/launcher-resolution-full.txt" 2>&1
RES_FULL=$?
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$PKG" > "$OUT/launcher-resolution.txt" 2>&1
RES=$?
COMPONENT=$(tail -1 "$OUT/launcher-resolution.txt" | tr -d '\r')
echo "resolve-activity exit=$RES (full=$RES_FULL) component=$COMPONENT" >> "$STATUS"
if [ "$COMPONENT" != "$PKG/com.civileg.app.ui.splash.SplashActivity" ]; then
  echo "LAUNCH RESOLUTION MISMATCH: expected $PKG/com.civileg.app.ui.splash.SplashActivity, got $COMPONENT" >> "$STATUS"
  echo "valid_png_count=0 launch_status=resolution-mismatch" >> "$STATUS"
  exit 1
fi
echo "launcher component verified: $COMPONENT" >> "$STATUS"

# --- Manifest proof from the built APK --------------------------------------
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1)
if [ -n "$AAPT" ]; then
  "$AAPT" dump badging "$APK" > "$OUT/apk-badging.txt" 2>&1
  grep -E "launchable-activity|package:" "$OUT/apk-badging.txt" >> "$STATUS" 2>/dev/null || true
else
  echo "aapt not found; badging unavailable" >> "$STATUS"
fi

# --- Grounded cold start: initial state, force-stop, verify ------------------
{
  echo "pid_before=$(adb shell pidof "$PKG" | tr -d '\r')"
  adb shell am force-stop "$PKG"; echo "force_stop_exit=$?"
  echo "pid_after_force_stop=$(adb shell pidof "$PKG" | tr -d '\r')"
} >> "$STATUS" 2>&1

adb logcat -c

# --- 2. Start the recorder BEFORE the launcher request -----------------------
# Fixed short limit, on-device, zero interaction. Host PID and monotonic
# start/ack bounds are retained so the launch request can be bound to the
# record timeline; ack is the device file appearing, which bounds recorder
# startup but not encoder first-frame time (see uncertainty note below).
adb shell rm -f "$REC_DEVICE"
REC_START=$(now_ms)
adb shell "screenrecord --time-limit $REC_LIMIT_S $REC_DEVICE" > "$OUT/screenrecord.stdout.txt" 2> "$OUT/screenrecord.stderr.txt" &
REC_PID=$!
REC_ACK=none
for i in $(seq 1 20); do
  if adb shell ls "$REC_DEVICE" > /dev/null 2>&1; then REC_ACK=$(now_ms); break; fi
  sleep 0.1
done
{
  echo "screenrecord host_pid=$REC_PID device_path=$REC_DEVICE limit_s=$REC_LIMIT_S"
  echo "recorder_start_monotonic_ms=$REC_START"
  echo "recorder_ack_monotonic_ms=$REC_ACK"
  if [ "$REC_ACK" != "none" ]; then
    echo "recorder_start_to_ack_ms=$((REC_ACK - REC_START))"
  fi
} >> "$STATUS"

# --- 3. Launch with monotonic request/return stamps --------------------------
T0=$(now_ms)
adb shell am start -n "$COMPONENT" > "$OUT/am-start.txt" 2>&1
AM_RC=$?
T1=$(now_ms)
{
  echo "am_start_request_monotonic_ms=$T0"
  echo "am_start_return_monotonic_ms=$T1"
  echo "am_start_roundtrip_ms=$((T1 - T0))"
  echo "am_start_exit=$AM_RC"
  echo "am_start_request_offset_from_recorder_start_ms=$((T0 - REC_START))"
  echo "note: T1 is when am start returned, not the launch/first-frame time"
  echo "note: request is bound to the record timeline by host monotonic stamps only; recorder startup plus encoder first-frame latency add uncertainty resolved exactly by the media PTS in the analysis step"
} >> "$TIMELINE"
if [ $AM_RC -ne 0 ]; then
  echo "am start failed rc=$AM_RC; see am-start.txt" >> "$STATUS"
  echo "valid_png_count=0 launch_status=am-start-failed" >> "$STATUS"
  collect_recording
  adb logcat -d > "$OUT/logcat.txt" 2>&1
  exit 1
fi

# --- 4. Sequential timed samples with actual elapsed -------------------------
VALID=0
for TARGET in 150 500 1000 2000 4000 8000; do
  NOW=$(now_ms)
  WAIT=$((TARGET - (NOW - T0)))
  if [ $WAIT -gt 0 ]; then sleep "$(awk "BEGIN{printf \"%.3f\", $WAIT/1000}")"; fi
  B=$(now_ms)
  adb exec-out screencap -p > "$OUT/t${TARGET}ms.png" 2> "$OUT/t${TARGET}ms.stderr"
  RC=$?
  E=$(now_ms)
  SIZE=$(stat -c%s "$OUT/t${TARGET}ms.png" 2>/dev/null || echo 0)
  SIG=$(head -c4 "$OUT/t${TARGET}ms.png" | od -An -tx1 | tr -d ' ')
  OK=no
  if [ $RC -eq 0 ] && [ "$SIZE" -gt 100 ] && [ "$SIG" = "89504e47" ]; then OK=yes; VALID=$((VALID+1)); fi
  {
    echo "sample t${TARGET}ms: requested_offset=${TARGET}ms begin=+$((B - T0))ms(req)/+$((B - T1))ms(ret) end=+$((E - T0))ms(req) rc=$RC bytes=$SIZE png_sig_ok=$([ "$SIG" = "89504e47" ] && echo yes || echo no) valid=$OK"
    adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r'
  } >> "$TIMELINE" 2>&1
done

# --- 5. Collect the recording (graceful wait on its own limit, pull first) --
collect_recording

adb logcat -d > "$OUT/logcat.txt" 2>&1
adb shell dumpsys window > "$OUT/dumpsys-window.txt" 2>&1
adb shell dumpsys activity activities > "$OUT/dumpsys-activities.txt" 2>&1
adb shell dumpsys SurfaceFlinger > "$OUT/dumpsys-surfaceflinger.txt" 2> "$OUT/dumpsys-surfaceflinger.stderr.txt"

echo "valid_png_count=$VALID of 6" >> "$STATUS"
if [ $VALID -eq 0 ]; then
  echo "launch_status=no-valid-captures" >> "$STATUS"
  exit 1
fi
echo "launch_status=captures-retained (interpretation left to pixel/focus review; recording validity recorded, frame advancement unproven until inspected)" >> "$STATUS"
exit 0
