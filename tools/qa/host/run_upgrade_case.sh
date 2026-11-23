#!/usr/bin/env bash
# Disposable synthetic-upgrade QA sequence. Runs on the emulator-runner host inside
# the android-emulator-runner script step, in the SAME job that built the candidate
# and re-signed the old APK copy, so one temporary debug signer covers both APKs.
#
# Boundaries: no uninstall/pm clear WITHIN a case (independent cases restart from a
# clean package state), no run-as against the non-debuggable builds, adb root only
# after the ro.kernel.qemu=1 disposable-emulator check, no global host security
# changes (network isolation is scoped to the disposable emulator's own radios and
# restored on EVERY exit path), no connectedAndroidTest as the upgrade executor.
#
# Cleanup is an EXIT trap with a dirty flag: it fires on explicit `exit`, set -e
# aborts and signals alike, preserves the original exit status, collects per-case
# evidence and restores radios whenever a case left the emulator dirty.
set -euo pipefail

PKG=com.civileg.app
DB=civil_eg_database
OUT=qa-out
mkdir -p "$OUT"

for v in CANDIDATE_APK TEST_APK OLD_APK_RESIGNED; do
  eval "p=\$$v"
  test -n "${p:-}" && test -f "$p" || { echo "missing or ambiguous input: $v=$p"; exit 1; }
done

SERIAL=$(adb devices | awk '/emulator-/{print $1; exit}')
test -n "$SERIAL" || { echo "no emulator found"; exit 1; }
ADB="adb -s $SERIAL"
$ADB shell getprop ro.kernel.qemu | tr -d '\r' | grep -q '^1$' || { echo "refusing: not a disposable emulator"; exit 1; }

# Scoped offline boundary: disable ONLY the disposable emulator's own radios, then
# VERIFY: the state capture must succeed, contain a recognizable CURRENT network
# section, and show no CONNECTED transport in that section. An empty or failed
# dump is never accepted as "offline". Restored on every exit path; no host-level
# network changes.
emu_network_off() {
  $ADB shell svc wifi disable
  $ADB shell svc data disable
  sleep 3
  # Bounded settling accommodation: the UNCHANGED fail-closed predicate below is
  # re-checked a fixed number of times after the one-time radio disable, because
  # a just-booted emulator can need longer than one 3s settle for its connectivity
  # inventory to drain. Every attempt's dump is saved; a failed or unrecognized
  # dump is NEVER accepted as offline; after the fixed bound we still refuse.
  # No parser relaxation, no timing-based acceptance, fail closed.
  local attempt=1 max_attempts=4
  local capture="$OUT/$CASE_DIRTY/connectivity-before-launch.txt"
  while [ "$attempt" -le "$max_attempts" ]; do
    local attempt_capture="$OUT/$CASE_DIRTY/connectivity-before-launch-attempt${attempt}.txt"
    if $ADB shell dumpsys connectivity > "$attempt_capture" 2>&1; then
      cp "$attempt_capture" "$capture"
    else
      local capture_rc=$?
      echo "connectivity capture attempt $attempt failed (rc=$capture_rc); not accepted as offline"
      if [ "$attempt" -eq "$max_attempts" ]; then
        echo "refusing: connectivity state capture failed after $max_attempts attempts (rc=$capture_rc)"
        return "$capture_rc"
      fi
      sleep 3
      attempt=$((attempt+1))
      continue
    fi
    if python3 - "$capture" <<'PY'
import pathlib, re, sys
text = pathlib.Path(sys.argv[1]).read_text().replace('\r', '')
active = re.search(r'^Active default network:\s*(.*?)\s*$', text, re.M)
section = re.search(r'^Current Networks:[ \t]*\n(.*?)^(?:Status for known UIDs:|Network Requests:)', text, re.M | re.S)
if not active or active.group(1).lower() != 'none' or not section:
    sys.exit('refusing: missing or non-offline current connectivity state')
# Conservative pinned-emulator boundary: require the current network inventory
# to be empty. Android 13 ends this inventory at Status for known UIDs;
# older minimal dumps can end it at Network Requests. Ignore later history.
if section.group(1).strip():
    sys.exit('refusing: current network inventory is not empty')
PY
    then
      echo "verified: disposable emulator offline (attempt $attempt of $max_attempts; saved current state; no default or current network)"
      return 0
    fi
    echo "offline predicate unmet on attempt $attempt (dump saved: $attempt_capture)"
    if [ "$attempt" -eq "$max_attempts" ]; then
      echo "refusing: missing or non-offline current connectivity state after $max_attempts bounded attempts (fail closed)"
      return 1
    fi
    sleep 3
    attempt=$((attempt+1))
  done
}
emu_network_on() {
  local failures=0
  $ADB shell svc wifi enable || failures=$((failures+1))
  $ADB shell svc data enable || failures=$((failures+1))
  if [ "$failures" -ne 0 ]; then
    echo "cleanup FAILURE: radio restoration failed"
    return 1
  fi
}

# Per-case evidence collection. Every capture failure is reported loudly and
# counted; the caller decides, but a failure NEVER passes silently.
collect_evidence() {
  local case_name=$1 dir="$OUT/$1" failures=0
  mkdir -p "$dir"
  $ADB logcat -d > "$dir/logcat.txt" 2>/dev/null || { echo "evidence FAILURE: logcat capture failed"; failures=$((failures+1)); }
  $ADB logcat -b crash -d > "$dir/logcat-crash.txt" 2>/dev/null || { echo "evidence FAILURE: crash-buffer logcat capture failed"; failures=$((failures+1)); }
  if $ADB shell screencap -p "/sdcard/qa-$case_name.png" >/dev/null 2>&1; then
    $ADB pull "/sdcard/qa-$case_name.png" "$dir/" >/dev/null 2>&1 || { echo "evidence FAILURE: screenshot pull failed"; failures=$((failures+1)); }
  else
    echo "evidence FAILURE: screencap failed"; failures=$((failures+1))
  fi
  $ADB pull "/data/data/$PKG/databases/$DB" "$dir/migrated-$DB" >/dev/null 2>&1 || { echo "evidence FAILURE: migrated DB pull failed"; failures=$((failures+1)); }
  return "$failures"
}

AGG_RC=0
CASE_DIRTY=""
on_exit() {
  local rc=$?
  trap - EXIT
  set +e
  if [ -n "$CASE_DIRTY" ]; then
    echo "== exit (status $rc) with case $CASE_DIRTY unfinished; collecting evidence and restoring radios =="
    collect_evidence "$CASE_DIRTY"
    cleanup_rc=$?
    emu_network_on
    radio_rc=$?
    if [ "$rc" -eq 0 ] && { [ "$cleanup_rc" -ne 0 ] || [ "$radio_rc" -ne 0 ]; }; then rc=1; fi
  fi
  exit "$rc"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Fail-closed instrumentation check: exact final OK count (singular "test" or
# plural "tests"), exactly N passing per-test status lines, no bad statuses.
check_instr() {
  local file=$1 expected=$2
  [ "$expected" -ge 1 ] || { echo "expected test count must be positive (fail-closed)"; return 1; }
  grep -qE "OK \($expected tests?\)" "$file" || { echo "instrumentation did not report OK ($expected test(s))"; return 1; }
  local passed bad
  passed=$(grep -c 'INSTRUMENTATION_STATUS_CODE: 0' "$file" || true)
  bad=$(grep -cE 'INSTRUMENTATION_STATUS_CODE: -[0-9]+' "$file" || true)
  [ "$passed" -eq "$expected" ] && [ "$bad" -eq 0 ] || { echo "unexpected per-test statuses: passed=$passed bad=$bad expected=$expected"; return 1; }
}

# One am instrument invocation. Returns nonzero on instrument/tee/check failure, never exits.
run_instrument() {
  local logfile=$1 expected=$2 classes=$3
  local instr_rc=0 tee_rc=0 check_rc=0
  local -a pipeline_rc
  set +e
  $ADB shell am instrument -w -r \
    -e class "$classes" \
    -e qaExpectations /data/local/tmp/qa_expectations.json \
    com.civileg.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee "$logfile"
  pipeline_rc=("${PIPESTATUS[@]}")
  instr_rc=${pipeline_rc[0]}
  tee_rc=${pipeline_rc[1]}
  set -e
  [ "$tee_rc" -eq 0 ] || echo "evidence FAILURE: tee of instrumentation output failed (rc=$tee_rc); $logfile may be incomplete"
  check_instr "$logfile" "$expected" || check_rc=$?
  [ "$instr_rc" -eq 0 ] || return "$instr_rc"
  [ "$tee_rc" -eq 0 ] || return "$tee_rc"
  return "$check_rc"
}

# Required companion proof: for every companion method the log reports as PASSED, its
# entry PNG+XML must exist and be non-empty and readable; the saved-model method also
# needs loaded-4-3 PNG+XML. Missing/empty proof fails evidence status (no acceptance).
require_companion_evidence() {
  local logfile=$1 evdir=$2 bad=0 name f
  local passed
  passed=$(awk '/^INSTRUMENTATION_STATUS: test=/{n=$0; sub(/^INSTRUMENTATION_STATUS: test=/,"",n)} /^INSTRUMENTATION_STATUS_CODE: 0/{if(n!=""){print n; n=""}}' "$logfile" | sort -u)
  for name in $passed; do
    local need="${name}-entry.png ${name}-entry.xml"
    case "$name" in savedFrameModelOpensFromRealSeededRecord) need="$need ${name}-loaded-4-3.png ${name}-loaded-4-3.xml";; esac
    for f in $need; do
      if [ ! -s "$evdir/$f" ]; then echo "evidence FAILURE: companion test $name passed but required proof $f is missing or empty"; bad=$((bad+1)); fi
    done
    for f in $need; do
      case "$f" in
        *.png) [ ! -s "$evdir/$f" ] || head -c 8 "$evdir/$f" | grep -q 'PNG' || { echo "evidence FAILURE: $f is not a readable PNG"; bad=$((bad+1)); } ;;
        *.xml) [ ! -s "$evdir/$f" ] || grep -q '<hierarchy' "$evdir/$f" || { echo "evidence FAILURE: $f is not a readable window hierarchy"; bad=$((bad+1)); } ;;
      esac
    done
  done
  [ -n "$passed" ] || echo "evidence NOTE: no companion method reported PASSED, so no success proof is required (failure evidence is best-effort)"
  return "$bad"
}

run_case() {
  local case_name=$1 expected_tests=$2 instrument_classes=$3
  local dir="$OUT/$case_name"
  mkdir -p "$dir"
  CASE_DIRTY=$case_name
  echo "== case $case_name =="

  echo "== scoped offline boundary for the disposable emulator =="
  emu_network_off

  # Independent case: start from a clean disposable package state.
  $ADB uninstall "$PKG" >/dev/null 2>&1 || true

  echo "== install re-signed old v1.8.0, real first launch (offline) =="
  $ADB install "$OLD_APK_RESIGNED"
  $ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 10
  $ADB shell am force-stop "$PKG"

  echo "== pull coherent DB set (root allowed: disposable emulator verified) =="
  $ADB root >/dev/null && sleep 2
  local remote=/data/data/$PKG/databases
  $ADB shell "test -f $remote/$DB" || { echo "old database file missing after first launch"; exit 1; }
  $ADB pull "$remote/$DB" "$dir/$DB" >/dev/null
  for side in wal shm; do
    if $ADB shell "test -f $remote/$DB-$side"; then
      $ADB pull "$remote/$DB-$side" "$dir/$DB-$side" >/dev/null
      echo "sidecar $DB-$side: present, pulled" | tee -a "$dir/sidecars.txt"
    else
      echo "sidecar $DB-$side: absent (optional)" | tee -a "$dir/sidecars.txt"
    fi
  done

  echo "== record actual old user_version, then seed sentinels on host =="
  python3 - "$dir/$DB" <<'PY'
import sqlite3, sys
v = sqlite3.connect(sys.argv[1]).execute("PRAGMA user_version").fetchone()[0]
print("actual old user_version:", v)
open(sys.argv[1] + ".user_version.txt", "w").write(str(v))
PY
  python3 tools/qa/host/seed_upgrade_db.py --db "$dir/$DB" --expectations "$dir/qa_expectations.json" --case "$case_name"

  echo "== push seeded DB back, restore ownership/perms/SELinux =="
  $ADB shell "rm -f $remote/$DB-wal $remote/$DB-shm"
  $ADB push "$dir/$DB" "$remote/$DB" >/dev/null
  UIDGID=$($ADB shell stat -c '%u:%g' "$remote" | tr -d '\r')
  $ADB shell chown "$UIDGID" "$remote/$DB"
  $ADB shell chmod 660 "$remote/$DB"
  $ADB shell restorecon "$remote/$DB" 2>/dev/null || true

  echo "== in-place upgrade: adb install -r candidate (same package, same disposable signer) =="
  $ADB install -r "$CANDIDATE_APK"
  $ADB shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
  sleep 15
  $ADB shell am force-stop "$PKG"

  echo "== instrumentation (fail-closed; per-command pipeline codes preserved) =="
  $ADB push "$dir/qa_expectations.json" /data/local/tmp/qa_expectations.json >/dev/null
  $ADB install -r "$TEST_APK" >/dev/null

  local crc=0
  case_instrument "$case_name" "$dir" "$expected_tests" "$instrument_classes" || crc=$?
  [ "$crc" -eq 0 ] || AGG_RC=1
}

# Instrumentation phase of a case. Called with `|| crc=$?` so one case failing does
# not stop the other; the SETUP part of run_case still runs under strict set -e.
case_instrument() {
  local case_name=$1 dir=$2 expected_tests=$3 instrument_classes=$4
  # Invocation 1: companion ALONE (own log/status/count), BEFORE the originals, so a
  # linkage abort in the original classes cannot prevent it from running. It does
  # not write app data; it only opens the already-migrated seeded DB through the UI.
  local comp_rc=0 comp_check=0 comp_ev=0
  CASE_COMPANION_DIR="$dir/companion"
  rm -rf "$CASE_COMPANION_DIR"; mkdir -p "$CASE_COMPANION_DIR"
  # Clear ONLY the per-case external companion evidence dir (never app data).
  $ADB shell "rm -rf /sdcard/Android/data/$PKG.test/files/companion" || true
  echo "== instrumentation 1/2: FrameEntryCompanionTest (expect 2) =="
  run_instrument "$dir/instrumentation-companion.txt" 2 "com.civileg.app.qa.FrameEntryCompanionTest" || comp_rc=$?
  $ADB pull "/sdcard/Android/data/$PKG.test/files/companion/." "$CASE_COMPANION_DIR/" >/dev/null 2>&1 || echo "evidence NOTE: companion dir pull returned nothing"
  require_companion_evidence "$dir/instrumentation-companion.txt" "$CASE_COMPANION_DIR" || comp_ev=$?

  # Invocation 2: the ORIGINAL classes, unchanged selection and counts, fail-closed.
  local orig_rc=0
  echo "== instrumentation 2/2: ORIGINAL classes (expect $expected_tests) =="
  run_instrument "$dir/instrumentation.txt" "$expected_tests" "$instrument_classes" || orig_rc=$?

  echo "== collect per-case evidence (before pass/fail decision) =="
  local ev_rc=0
  collect_evidence "$case_name" || ev_rc=$?
  emu_network_on
  CASE_DIRTY=""

  echo "== case $case_name RESULTS: companion instrument/check rc=$comp_rc, companion evidence rc=$comp_ev; original rc=$orig_rc; evidence rc=$ev_rc =="
  if [ "$comp_rc" -ne 0 ] || [ "$comp_ev" -ne 0 ] || [ "$orig_rc" -ne 0 ] || [ "$ev_rc" -ne 0 ]; then
    echo "== case $case_name FAILED; evidence in $dir =="
    return 1
  fi
  echo "== case $case_name PASSED =="
}

run_case "fresh-14" 5 \
  "com.civileg.app.qa.UpgradePreservationTest,com.civileg.app.qa.MinifiedSourceSnapshotTest,com.civileg.app.qa.FrameEntryTest"
# Case 2 runs even if case 1 failed so both results are real; nothing is marked
# passed or skipped silently. The aggregate stays red if either case failed.
run_case "historical-8to9-derived" 3 \
  "com.civileg.app.qa.UpgradePreservationTest,com.civileg.app.qa.FrameEntryTest"
if [ "$AGG_RC" -ne 0 ]; then
  echo "== synthetic upgrade QA: AT LEAST ONE CASE FAILED (see RESULTS lines) =="
  exit 1
fi
echo "== synthetic upgrade QA: all cases PASSED =="
