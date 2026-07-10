#!/usr/bin/env python3
"""Bulk fix common compilation errors in civileg2 drawing files."""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg2"

def read(path):
    with open(path, 'r') as f:
        return f.read()

def write(path, content):
    with open(path, 'w') as f:
        f.write(content)

# Files to fix
FILES = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
    "ui/compose/screens/BeamScreen.kt",
    "ui/compose/screens/ColumnScreen.kt",
    "ui/compose/screens/FrameAnalysisScreen.kt",
    "ui/compose/screens/FrameDrawingCanvas.kt",
    "ui/compose/screens/HomeScreen.kt",
    "ui/compose/screens/SeismicScreen.kt",
    "ui/compose/screens/StairScreen.kt",
    "ui/compose/screens/SteelDesignScreen.kt",
]

fixes = 0

for rel_path in FILES:
    fpath = os.path.join(BASE, rel_path)
    if not os.path.exists(fpath):
        continue
    
    content = read(fpath)
    original = content
    
    # 1. Add kotlin.math.* import if pow/sqrt/abs/min/max/minOf/maxOf used but not imported
    needs_math = False
    for func in ['pow', 'sqrt', 'abs', 'minOf', 'maxOf']:
        if f'kotlin.math.{func}' not in content:
            # Check if the function is used in code (not in a comment)
            # Simple check: look for func( or func. usage
            if re.search(rf'\b{func}\s*[\(\.]', content):
                needs_math = True
                break
    
    if needs_math:
        # Check if there's already a kotlin.math import
        if 'import kotlin.math' not in content:
            # Add after the package statement
            content = re.sub(
                r'(package com\.civileg\.app[^\n]*)',
                r'\1\nimport kotlin.math.*',
                content,
                count=1
            )
            if content != original:
                print(f"  [math import] {rel_path}")
                fixes += 1
                original = content
    
    # 2. Fix Offset() internal constructor - these are usually from Double args
    # Pattern: Offset(someDouble, otherDouble) where Offset expects Float
    # Add .toFloat() to the args
    # But be careful not to change Offset(floatVal, floatVal) which is already correct
    # We'll skip this for now as it's complex
    
    # 3. Fix Paint.color = Color -> Paint.color = color.toArgb()
    content = re.sub(
        r'(\w+)\.color\s*=\s*color\b(?!\.toArgb)',
        r'\1.color = color.toArgb()',
        content
    )
    
    # 4. Fix this.color = color -> this.color = color.toArgb()
    content = re.sub(
        r'this\.color\s*=\s*color\b(?!\.toArgb)',
        r'this.color = color.toArgb()',
        content
    )
    
    # 5. Fix paint.color = textColor -> paint.color = textColor.toArgb()
    content = re.sub(
        r'(\w+)\.color\s*=\s*textColor\b(?!\.toArgb)',
        r'\1.color = textColor.toArgb()',
        content
    )
    
    # 6. Fix color = Color(...) passed to Int parameter: .color = Color(...).toArgb()
    # Pattern: .color = Color(...) where the next char is NOT a dot
    content = re.sub(
        r'\.color\s*=\s*(Color\([^)]+)\)(?!\.)',
        r'.color = \1.toArgb()',
        content
    )
    
    if content != original:
        print(f"  [toArgb] {rel_path}")
        fixes += 1
        original = content
    
    # 7. Fix max(Float/Double, floatLiteral) -> ensure consistent Float
    # max(a, b) where a is Double and b is Float
    # Pattern: max(someExpr, 2.5f) where someExpr might be Double
    # Add .toFloat() before the closing paren
    content = re.sub(
        r'\bmax\(([^)]+)\)\.toFloat\(\)',
        lambda m: f'max({m.group(1)}).toFloat()',
        content
    )
    
    if content != original:
        fixes += 1
    
    # 8. Fix min() with 3+ args -> minOf()
    content = re.sub(
        r'\bmin\(([^,]+,[^,]+,[^)]+)\)',
        lambda m: f'minOf({m.group(0)})',
        content
    )
    
    # 9. Fix max() with 3+ args -> maxOf()
    content = re.sub(
        r'\bmax\(([^,]+,[^,]+,[^)]+)\)',
        lambda m: f'maxOf({m.group(0)})',
        content
    )
    
    if content != original:
        print(f"  [min/maxOf] {rel_path}")
        fixes += 1
        original = content
    
    if content != original:
        write(fpath, content)
        print(f"  SAVED: {rel_path} ({content.count(chr(10))} lines)")

print(f"\nTotal fixes applied: {fixes}")