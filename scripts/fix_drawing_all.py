#!/usr/bin env python3
"""Comprehensive fix for Kotlin compilation errors in drawing files.
Handles: pow/sqrt imports, Offset(Double,Double)->Float, Color->toArgb, 
max/min->maxOf/minOf, forEach->for, @Composable in forEach."""
import re, os, sys

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg2"

DRAWING_FILES = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
]

def read(p):
    try:
        with open(p, 'r') as f: return f.read()
    except: return ""

def write(p, c):
    try:
        with open(p, 'w') as f: f.write(c)
    except: return ""

fixes_total = 0

for rel in DRAWING_FILES:
    fp = os.path.join(BASE, rel)
    c = read(fp)
    if not c: continue
    orig = c
    
    # 1. Add kotlin.math.* if pow/sqrt/abs used but not imported
    math_funcs_used = set()
    for func in ['pow', 'sqrt', 'abs', 'minOf', 'maxOf', 'min', 'max']:
        # Check usage (not in comment, not in string literal)
        for m in re.finditer(rf'\b{func}\s*\(', c):
            if '//' in c[:m.start()]: continue
            math_funcs_used.add(func)
    
    need_math_import = math_funcs_used and 'import kotlin.math' not in c
    
    if need_math_import:
        c = re.sub(
            r'(package\s+com\.civileg\.app\.[^\n]+)',
            r'\1\nimport kotlin.math.*\n',
            c, count=1
        )
    
    # 2. Fix Color -> toArgb() patterns
    # Pattern: .color = Color(...) or paint.color = Color(...)
    c = re.sub(
        r'\.color\s*=\s*(Color\([^)]+)\)(?!\.(?:toArgb|toInt))',
        r'\1.toArgb()',
        c
    )
    c = re.sub(
        r'\.color\s*=\s*(color)\b(?!\.toArgb)',
        r'\1.toArgb()',
        c
    )
    c = re.sub(
        r'\.color\s*=\s*(textColor)\b(?!\.toArgb)',
        r'\1.toArgb()',
        c
    )
    
    # 3. Fix Offset(Double, Double) - add .toFloat() to both args
    # We need to find Offset( and add .toFloat() to numeric args
    # Simple approach: find Offset(, parse balanced parens, fix args
    new_lines = []
    changes = 0
    
    for line in c.split('\n'):
        stripped = line.lstrip()
        if '//' in stripped or 'Offset(' not in stripped:
            new_lines.append(line)
            continue
        
        idx = 0
        while True:
            pos = line.find('Offset(', idx)
            if pos == -1:
                new_lines.append(line)
                break
            
            # Find matching close paren
            depth = 1
            end = pos + 7
            while end < len(line) and depth > 0:
                if line[end] == '(': depth += 1
                elif line[end] == ')': depth -= 1
                if depth == 0: break
                end += 1
            
            # Extract args
            args_str = line[pos+7:end-1]
            before = line[:pos+7]
            after = line[end-1:]
            
            # Parse args and add .toFloat() to numeric expressions
            args = args_str.split(',')
            new_args = []
            for arg in args:
                a = arg.strip()
                if not a: new_args.append(a); continue
                
                # Skip if already has .toFloat()
                if '.toFloat()' in a: new_args.append(a); continue
                
                # Skip string literals
                if a.startswith('"') or a.startswith("'"): new_args.append(a); continue
                
                # Check if it's a math expression (contains operators)
                has_op = any(op in a for op in ['+', '-', '*', '/', 'pow', 'sqrt', 'sin', 'cos', 'tan', 'max', 'min', 'abs', 'atan2', 'log', 'log10', 'PI'])
                
                if has_op:
                    new_args.append(f'({a}).toFloat()')
                else:
                    # Check if it could be a variable/property that's numeric
                    if re.match(r'^-?[\d.]+f$', a) or re.match(r'^-?[\d.]+$', a):
                        new_args.append(a + '.toFloat()')
                    else:
                        new_args.append(a)
            
            new_line = before + ', '.join(new_args) + after
            idx = pos + 7
            changes += 1
        
        new_lines.append(line)
    
    c = '\n'.join(new_lines)
    
    # 4. Fix max(a, b) / min(a, b) with 3+ args -> maxOf / minOf
    c = re.sub(r'\bmax\(([^,]+,[^,]+)\)', r'maxOf(\1)', c)
    c = re.sub(r'\bmin\(([^,]+,[^,]+)\)', r'minOf(\1)', c)
    c = re.sub(r'\bmax\(([^,]+,[^,]+,[^,]+)\)', r'maxOf(\1)', c)
    c = re.sub(r'\bmin\(([^,]+,[^,]+,[^,]+)\)', r'minOf(\1)', c)
    
    if c != orig:
        write(fp, c)
        print(f"  {rel}: math_import={need_math_import}, toArgb={c != orig}, args_fixes={changes}")
        fixes_total += changes

print(f"\nTotal fixes across all files: {fixes_total}")