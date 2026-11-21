#!/usr/bin/env python3
"""Guard: an engine/entity class must live in exactly one module.

Single-source-of-truth rule (AGENT_MASTER_PLAN Phase 2, risk T2): once a
calculation engine or entity exists under core/calculations, no class with
the same name may remain under app/domain/calculations, and vice versa.

Known pre-existing duplicates live in duplicate_engines_baseline.json and
must burn down as Phase 2 migrates each family into core. The guard fails
on (a) any duplicate NOT in the baseline and (b) any baseline entry whose
duplicate was fixed - so the baseline can only shrink.
"""
import json
import re
import sys
from pathlib import Path

APP_TREE = Path("app/src/main/java/com/civileg/app/domain/calculations")
CORE_TREE = Path("core/calculations/src/main/kotlin")
BASELINE = Path("tools/guards/duplicate_engines_baseline.json")

# Top-level declarations only (no leading whitespace): nested Result/Inputs
# holder classes are scoped to their parent and are not cross-module dupes.
DECL = re.compile(r"^(?:@[\w.()\"', =]+\s*)*(?:public\s+|internal\s+|private\s+)?(?:data\s+|sealed\s+|abstract\s+|open\s+|enum\s+|annotation\s+)?(?:class|object|interface)\s+(\w+)")

def classes(root):
    found = {}
    if not root.is_dir():
        return found
    for f in root.rglob("*.kt"):
        try:
            for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
                m = DECL.match(line)
                if m:
                    found.setdefault(m.group(1), []).append(str(f))
        except OSError:
            pass
    return found

def main():
    app = classes(APP_TREE)
    core = classes(CORE_TREE)
    dupes = sorted(set(app) & set(core))
    baseline = set()
    if BASELINE.is_file():
        baseline = set(json.loads(BASELINE.read_text(encoding="utf-8")))
    current = set(dupes)
    new_dupes = sorted(current - baseline)
    fixed = sorted(baseline - current)
    ok = True
    if new_dupes:
        ok = False
        print("FAIL: NEW duplicate engine/entity declarations across app and core:")
        for name in new_dupes:
            print(f"  {name}:")
            for p in app[name]:
                print(f"    app : {p}")
            for p in core[name]:
                print(f"    core: {p}")
    if fixed:
        ok = False
        print("FAIL: these baseline duplicates were fixed - remove them from duplicate_engines_baseline.json:")
        for name in fixed:
            print(f"  {name}")
    if ok:
        print(f"OK: no new duplicates ({len(current)} known, burning down).")
    return 0 if ok else 1

if __name__ == "__main__":
    sys.exit(main())
