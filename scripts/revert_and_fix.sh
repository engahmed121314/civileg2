#!/bin/bash
# Comprehensive fix: revert broken files, apply correct fixes, build
set -e
cd /home/z/my-project/civileg2

echo "=== STEP 1: Identify files with _nc errors (broken by script) ==="
BROKEN_FILES=""
for f in $(git diff --name-only HEAD); do
    if rg -l '_nc' "$f" 2>/dev/null; then
        # Check if file has _nc but no "val _nc" capture
        if rg '_nc' "$f" >/dev/null 2>&1 && ! rg 'val _nc = nativeCanvas' "$f" >/dev/null 2>&1; then
            BROKEN_FILES="$BROKEN_FILES $f"
            echo "BROKEN (no capture): $f"
        elif rg '_nc' "$f" >/dev/null 2>&1; then
            echo "HAS _nc capture: $f"
        fi
    fi
done

echo ""
echo "=== STEP 2: Revert _nc/_d/_size replacements in broken files ==="
python3 << 'PYEOF'
import re, os, subprocess

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"

# Files to revert (broken by _nc replacement)
REVERT_FILES = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/MomentShearForceDiagram.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalBeamDrawing.kt",
]

# Files to keep agent fixes but remove bad _nc
KEEP_BUT_FIX = [
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
    "ui/compose/screens/StairScreen.kt",
    "ui/compose/screens/SeismicScreen.kt",
    "ui/compose/components/drawings/InteractiveDrawingScreen.kt",
]

for f in REVERT_FILES + KEEP_BUT_FIX:
    fpath = os.path.join(BASE, f)
    if not os.path.exists(fpath):
        print(f"SKIP (not found): {f}")
        continue
    
    with open(fpath, 'r', encoding='utf-8') as fh:
        content = fh.read()
    
    orig = content
    
    # Revert _nc → nativeCanvas
    content = content.replace('_nc.', 'nativeCanvas.')
    content = content.replace('val _nc = nativeCanvas', '__REMOVE_THIS_LINE__')
    content = content.replace('val _d = density', '__REMOVE_THIS_LINE__')
    content = content.replace('val _density = density', '__REMOVE_THIS_LINE__')
    content = content.replace('val _size = size', '__REMOVE_THIS_LINE__')
    content = content.replace('* _d', '* density')
    content = content.replace('_d *', 'density *')
    content = content.replace('_size.width', 'size.width')
    content = content.replace('_size.height', 'size.height')
    
    # Remove the capture lines
    lines = content.split('\n')
    lines = [l for l in lines if '__REMOVE_THIS_LINE__' not in l]
    content = '\n'.join(lines)
    
    if f in REVERT_FILES:
        # Also revert v2 script changes: _ds references
        content = content.replace('_ds.nativeCanvas', 'nativeCanvas')
        content = content.replace('_ds.density', 'density')
        content = content.replace('val _ds: DrawScope = this', '__REMOVE__')
        lines = content.split('\n')
        lines = [l for l in lines if '__REMOVE__' not in l]
        content = '\n'.join(lines)
    
    if content != orig:
        with open(fpath, 'w', encoding='utf-8') as fh:
            fh.write(content)
        print(f"REVERTED _nc refs: {f}")
    else:
        print(f"NO CHANGE: {f}")

print("\nRevert complete.")
PYEOF

echo ""
echo "=== STEP 3: Check remaining _nc references ==="
rg "_nc\b" /home/z/my-project/civileg2/app/src/main/java/ -l 2>/dev/null || echo "No _nc references remaining (good!)"