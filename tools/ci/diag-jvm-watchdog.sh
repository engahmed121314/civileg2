#!/bin/bash
# Diagnostic only, read-only. After $1 seconds, then again 120 s later, snapshot thread stacks and heap info of every
# JVM (Gradle test workers included) to stdout AND to $2. Helper calls are hard time-bounded (timeout -k 2s).
# No GC, no kill/signal of any discovered JVM, no heap dump. Cleanup signals only this script's own sleep/helper children.
D="$2"; mkdir -p "$D"; L="$D/watchdog.log"
say() { echo "$@" | tee -a "$L"; }
KID=""
cleanup() { [ -n "$KID" ] && kill "$KID" 2>/dev/null; wait 2>/dev/null; exit 0; }
trap cleanup TERM INT EXIT
nap() { sleep "$1" & KID=$!; wait "$KID"; KID=""; }
run() { out="$1"; shift; "$@" > "$out" 2> "$out.err" & KID=$!; wait "$KID"; rc=$?; KID=""; return $rc; }
say "WATCHDOG start $(date -u +%T) deadline_after=${1}s"
for c in jps jstack jcmd timeout; do say "WATCHDOG tool $c: $(command -v $c || echo MISSING)"; done
nap "$1"
for round in 1 2; do
  say "WATCHDOG round $round at $(date -u +%T)"
  run "$D/r$round-jps.txt" timeout -k 2s 5s jps -l; say "WATCHDOG jps exit=$?"; tee -a "$L" < "$D/r$round-jps.txt"
  for pid in $(awk '$2 !~ /Jps$/ {print $1}' "$D/r$round-jps.txt"); do
    name=$(awk -v p="$pid" '$1==p{print $2}' "$D/r$round-jps.txt")
    say "=== pid $pid $name round $round ==="
    run "$D/r$round-$pid.jstack.txt" timeout -k 2s 15s jstack "$pid"; say "jstack exit=$?"
    n=$(wc -l < "$D/r$round-$pid.jstack.txt")
    case "$name" in
      *GradleWorkerMain*) cat "$D/r$round-$pid.jstack.txt" "$D/r$round-$pid.jstack.txt.err";;
      *) say "(stdout truncated to first 150 of $n lines; full file in artifact if uploaded)"; head -150 "$D/r$round-$pid.jstack.txt"; cat "$D/r$round-$pid.jstack.txt.err";;
    esac
    run "$D/r$round-$pid.heap.txt" timeout -k 2s 10s jcmd "$pid" GC.heap_info; say "heap_info exit=$?"
    head -40 "$D/r$round-$pid.heap.txt" "$D/r$round-$pid.heap.txt.err"
  done
  [ "$round" = 1 ] && nap 120
done
say "WATCHDOG done $(date -u +%T)"
