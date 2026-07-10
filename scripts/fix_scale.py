#!/usr/bin/env python3
"""Fix Double * scale -> Float type mismatches in drawing files by wrapping
the left operand in .toFloat()."""

import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg2"

FILES = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
]

def read(path):
    with open(path, 'r') as f:
        return f.read()

def write(path, content):
    with open(path, 'w') as f:
        f.write(content)

total = 0
for rel_path in FILES:
    fpath = os.path.join(BASE, rel_path)
    if not os.path.exists(fpath):
        continue
    
    content = read(fpath)
    original = content
    
    # Fix: expr * scale -> (expr).toFloat() * scale
    # where expr is any expression (not already wrapped in parens with .toFloat)
    # Pattern: non-paren expression * scale (but NOT (expr).toFloat() * scale)
    # Use negative lookbehind to avoid re-matching
    
    # Simple approach: find `<something> * scale` where <something> doesn't start with ( and doesn't contain .toFloat()
    # Replace with `(<something>).toFloat() * scale`
    
    new_content = re.sub(
        r'(?<!\.)'  # negative lookbehind: not after . (partial)
        r'('
        r'(?!\()'  # negative lookahead: not starting with ( after first (
        r'[^()]*?)'  # the expression (no nested parens)
        r')\s*\*\*\s*scale\b',  # * scale
        lambda m: f'({m.group(1)}).toFloat() * scale',
        content
    )
    
    # Also fix: scale * expr
    new_content = re.sub(
        r'(?<!\.)\bscale\s*\*\s*\('
        r'(?!\()'
        r'[^()]*?'
        r')',
        lambda m: f'scale * ({m.group(1)}).toFloat()',
        new_content
    )
    
    # Fix: expr / 2f where expr might be Double -> ((expr) / 2.0).toFloat()
    # Only in drawing files where scale issues exist
    new_content = re.sub(
        r'(?<!\.)\(([^()]+)\)\s*/\s*2f\b',
        lambda m: f'(({m.group(1)} / 2.0)).toFloat()',
        new_content
    )
    
    if new_content != original:
        write(fpath, new_content)
        diff = len(new_content) - len(original)
        print(f"  {rel_path}: {diff} chars changed")
        total += 1

print(f"\nTotal files modified: {total}")