#!/usr/bin/env bash
# Bounded real-launch evidence for Splash Treatment B.
# Resolves the installed MAIN/LAUNCHER component via the package manager,
# verifies it is the intended SplashActivity, force-stops the disposable app
# for a grounded cold start, then captures the actual display at target
# offsets after the am start REQUEST, recording the real monotonic elapsed
# of every sample (begin/end) relative to both request and return.
# No taps, no input, no consent/ad/navigation interaction; whatever the app
# shows is captured as-is. Diagnostic only; it never gates CI, and it exits
# nonzero when the launch or every capture failed - no success claims.
set -u
OUT=splash-launch-evidence
mkdir -p "$OUT"
PKG=com.civileg.app.debug
APK=app/build/outputs/apk/free/debug/app-free-debug.apk
STATUS="$OUT/status.txt"
TIMELINE="$OUT/timeline.txt"
: > "$STATUS"; : > "$TIMELINE"

now_ms() { date +%s%3N; }

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

# --- 2. Launch with monotonic request/return stamps --------------------------
T0=$(now_ms)
adb shell am start -n "$COMPONENT" > "$OUT/am-start.txt" 2>&1
AM_RC=$?
T1=$(now_ms)
{
  echo "am_start_request_monotonic_ms=$T0"
  echo "am_start_return_monotonic_ms=$T1"
  echo "am_start_roundtrip_ms=$((T1 - T0))"
  echo "am_start_exit=$AM_RC"
  echo "note: T1 is when am start returned, not the launch/first-frame time"
} >> "$TIMELINE"
if [ $AM_RC -ne 0 ]; then
  echo "am start failed rc=$AM_RC; see am-start.txt" >> "$STATUS"
  echo "valid_png_count=0 launch_status=am-start-failed" >> "$STATUS"
  adb logcat -d > "$OUT/logcat.txt" 2>&1
  exit 1
fi

# --- 3. Sequential timed samples with actual elapsed -------------------------
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

adb logcat -d > "$OUT/logcat.txt" 2>&1
adb shell dumpsys window > "$OUT/dumpsys-window.txt" 2>&1
adb shell dumpsys activity activities > "$OUT/dumpsys-activities.txt" 2>&1
adb shell dumpsys SurfaceFlinger > "$OUT/dumpsys-surfaceflinger.txt" 2> "$OUT/dumpsys-surfaceflinger.stderr.txt"

echo "valid_png_count=$VALID of 6" >> "$STATUS"
if [ $VALID -eq 0 ]; then
  echo "launch_status=no-valid-captures" >> "$STATUS"
  exit 1
fi
echo "launch_status=captures-retained (interpretation left to pixel/focus review)" >> "$STATUS"
exit 0
