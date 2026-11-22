#!/usr/bin/env bash
# Local host fixtures for the bounded emu_network_off recheck in
# run_upgrade_case.sh. No emulator, no network: ADB is a stub that replays
# canned dumpsys-connectivity fixtures; sleep is a counting stub. Proves:
#  - immediate offline accepted on attempt 1
#  - connected-then-offline accepted with the LAST accepted dump recorded
#  - always-connected refuses after exactly 4 captures / 3 inter-attempt sleeps
#  - empty/unrecognized dumps are never accepted as offline
#  - dump capture failure refuses (fail closed) and never installs
#  - final refusal leaves the case unlaunched (no install marker)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Extract the function under test verbatim from the real script.
awk '/^emu_network_off\(\)/,/^}$/' "$HERE/run_upgrade_case.sh" > "$WORK/fn.sh"
grep -q "emu_network_off()" "$WORK/fn.sh" || { echo "FAIL: could not extract emu_network_off"; exit 2; }

OFFLINE_DUMP="$WORK/offline.dump"
CONNECTED_DUMP="$WORK/connected.dump"
EMPTY_DUMP="$WORK/empty.dump"
GARBAGE_DUMP="$WORK/garbage.dump"
cat > "$OFFLINE_DUMP" <<'D'
Active default network: none

Current Networks:

Status for known UIDs:
  1000
D
cat > "$CONNECTED_DUMP" <<'D'
Active default network: 103

Current Networks:
   NetworkAgentInfo{ ni{[type: WIFI[]} ni{CONNECTED} }

Status for known UIDs:
  1000
D
: > "$EMPTY_DUMP"
cat > "$GARBAGE_DUMP" <<'D'
garbage unrecognized dump text
D

# Canned fixture sequence file: one fixture path per line, or FAIL.
SEQ="$WORK/seq.txt"
LOG="$WORK/log.txt"
: > "$LOG"

cat > "$WORK/fake-adb.sh" <<'FAKE'
#!/usr/bin/env bash
# $SEQ points to a file whose first line is the next fixture (or FAIL).
if [ "$1 $2 $3" = "shell svc wifi" ] || [ "$1 $2 $3" = "shell svc data" ]; then
  exit 0
fi
if [ "$1 $2" = "shell dumpsys" ]; then
  next="$(head -1 "$SEQ" 2>/dev/null)"
  [ -n "$next" ] && sed -i '1d' "$SEQ"
  if [ "$next" = "FAIL" ] || [ -z "$next" ]; then
    echo "adb: error" >&2
    exit 1
  fi
  cat "$next"
  exit 0
fi
exit 0
FAKE
chmod +x "$WORK/fake-adb.sh"

run_case() {
  local name="$1"; shift
  printf '%s\n' "$@" > "$SEQ"
  export SEQ
  : > "$LOG"
  rm -rf "$WORK/out" "$WORK/INSTALLED"
  (
    ADB="bash $WORK/fake-adb.sh"
    OUT="$WORK/out"
    CASE_DIRTY="case"
    mkdir -p "$OUT/$CASE_DIRTY"
    sleep() { echo "SLEEP $1" >> "$LOG"; }
    . "$WORK/fn.sh"
    if emu_network_off; then
      echo "ACCEPTED" >> "$LOG"
      touch "$WORK/INSTALLED"   # caller would proceed to install
    else
      echo "REFUSED rc=$?" >> "$LOG"
    fi
  )
  local rc_line captures sleeps accepted
  rc_line="$(grep -E '^(ACCEPTED|REFUSED)' "$LOG" || echo NONE)"
  captures="$(ls "$WORK/out/case"/connectivity-before-launch-attempt*.txt 2>/dev/null | wc -l | tr -d ' ')"
  sleeps="$(grep -c '^SLEEP 3$' "$LOG")"   # includes the one-time post-disable settle
  local inter_sleeps=$(( sleeps > 0 ? sleeps - 1 : 0 ))
  echo "== $name: $rc_line captures=$captures loop_sleeps=$inter_sleeps installed=$([ -f "$WORK/INSTALLED" ] && echo yes || echo no)"
  RESULTS+=("$name|$rc_line|$captures|$inter_sleeps|$([ -f "$WORK/INSTALLED" ] && echo yes || echo no)")
}

declare -a RESULTS=()
run_case immediateoffline        "$OFFLINE_DUMP"
run_case connectedthenoffline    "$CONNECTED_DUMP" "$OFFLINE_DUMP"
run_case alwaysconnected         "$CONNECTED_DUMP" "$CONNECTED_DUMP" "$CONNECTED_DUMP" "$CONNECTED_DUMP" "$CONNECTED_DUMP"
run_case empty_unrecognized      "$EMPTY_DUMP" "$GARBAGE_DUMP" "$EMPTY_DUMP" "$GARBAGE_DUMP" "$EMPTY_DUMP"
run_case dumpfailure             FAIL FAIL FAIL FAIL FAIL
run_case finalfailure_noinstall  "$CONNECTED_DUMP" "$CONNECTED_DUMP" "$CONNECTED_DUMP" "$CONNECTED_DUMP" "$CONNECTED_DUMP"

fail=0
check() { # name field expected actual
  if [ "$3" != "$4" ]; then echo "FAIL: $1 $2 expected [$3] got [$4]"; fail=1; fi
}
for r in "${RESULTS[@]}"; do
  IFS='|' read -r name rc caps slps inst <<< "$r"
  case "$name" in
    immediateoffline)
      check "$name" result ACCEPTED "$rc"; check "$name" captures 1 "$caps"
      check "$name" loop_sleeps 0 "$slps"; check "$name" installed yes "$inst"
      ;;
    connectedthenoffline)
      check "$name" result ACCEPTED "$rc"; check "$name" captures 2 "$caps"
      check "$name" loop_sleeps 1 "$slps";;
    alwaysconnected)
      check "$name" result "REFUSED rc=1" "$rc"; check "$name" captures 4 "$caps"
      check "$name" loop_sleeps 3 "$slps"; check "$name" installed no "$inst";;
    empty_unrecognized)
      check "$name" result "REFUSED rc=1" "$rc"; check "$name" captures 4 "$caps"
      check "$name" installed no "$inst";;
    dumpfailure)
      check "$name" result "REFUSED rc=1" "$rc"; check "$name" installed no "$inst";;
    finalfailure_noinstall)
      check "$name" result "REFUSED rc=1" "$rc"; check "$name" captures 4 "$caps"
      check "$name" loop_sleeps 3 "$slps"; check "$name" installed no "$inst";;
  esac
done

# Prove the LAST ACCEPTED dump for connectedthenoffline is the offline one:
# rerun that case and diff the surviving canonical capture against fixtures.
printf '%s\n' "$CONNECTED_DUMP" "$OFFLINE_DUMP" > "$SEQ"
: > "$LOG"; rm -rf "$WORK/out"
(
  ADB="bash $WORK/fake-adb.sh"; OUT="$WORK/out"; CASE_DIRTY="case"
  mkdir -p "$OUT/$CASE_DIRTY"
  sleep() { :; }
  . "$WORK/fn.sh"
  emu_network_off
)
if cmp -s "$WORK/out/case/connectivity-before-launch.txt" "$OFFLINE_DUMP"; then
  echo "== last-accepted-dump: canonical capture IS the attempt-2 offline dump"
else
  echo "FAIL: canonical capture is not the last accepted offline dump"; fail=1
fi

if [ "$fail" -eq 0 ]; then echo "ALL FIXTURE CHECKS PASSED"; else echo "FIXTURE FAILURES PRESENT"; exit 1; fi
