#!/usr/bin/env python3
"""Heuristic dead-code audit for the CivilEG2 Android source tree.

Usage (from the repository root):

    python tools/audit/dead_code_audit.py

Output:
  1. declarations with no reference outside their own declaration;
  2. files whose *every* top-level declaration is unreferenced.

The audit is a *candidate* list, not a delete list. Before removing anything,
check DI (@Module/@Inject), Room entities, reflection/Gson, XML layouts,
AndroidManifest.xml and test fixtures. See docs/UNUSED_CODE_AUDIT.md.
"""
from __future__ import annotations

import re
from collections import Counter
from pathlib import Path

APP_SRC = Path("app/src")
CORE_SRC = Path("core")
PRODUCTION = [p for p in APP_SRC.rglob("*.kt") if {"test", "androidTest"}.isdisjoint(p.parts)]
EVERYTHING = list(APP_SRC.rglob("*.kt")) + list(CORE_SRC.rglob("*.kt"))

IDENTIFIER = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
DECLARATION = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public |internal |abstract |open |sealed |data |enum |annotation |value |inline |expect |actual )*"
    r"(class|object|interface|fun)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)


def read(paths: list[Path]) -> dict[Path, str]:
    sources: dict[Path, str] = {}
    for path in paths:
        try:
            sources[path] = path.read_text(encoding="utf-8", errors="replace")
        except OSError as error:
            print(f"warning: cannot read {path}: {error}")
    return sources


production = read(PRODUCTION)
all_sources = read(EVERYTHING)

counts_everywhere = Counter()
for text in all_sources.values():
    counts_everywhere.update(IDENTIFIER.findall(text))

declarations: dict[str, list[tuple[Path, str]]] = {}
for path, text in production.items():
    for kind, name in DECLARATION.findall(text):
        declarations.setdefault(name, []).append((path, kind))


def external_references(name: str) -> int:
    """Occurrences minus the declaration line itself."""
    return counts_everywhere[name] - len(declarations[name])


unreferenced = sorted(
    ((name, defs[0]) for name, defs in declarations.items()
     if len(name) > 3 and external_references(name) <= 0),
    key=lambda item: (str(item[1][0]), item[0]),
)

print(f"production Kotlin files : {len(production)}")
print(f"top-level declarations  : {len(declarations)}")
print(f"unreferenced candidates : {len(unreferenced)}\n")

for name, (path, kind) in unreferenced:
    print(f"{kind:9s} {name:45s} {path}")

# Files where every declaration is unreferenced.
print("\n== files with no externally referenced declaration ==")
for path, text in sorted(production.items()):
    names = [name for _kind, name in DECLARATION.findall(text)]
    if names and all(len(name) > 3 and external_references(name) <= 0 for name in names):
        print(f"{len(names):2d} decls  {path}")
