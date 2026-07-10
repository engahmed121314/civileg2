#!/usr/bin/env python3
"""Fix Offset(Double, Double) -> Offset internal constructor errors by adding .toFloat() to args."""

import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg2"

# Get files that have Offset internal errors
ERROR_FILES = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
    "ui/compose/screens/BeamScreen.kt",
    "ui/compose/screens/ColumnScreen.kt",
    "ui/compose/screens/FrameDrawingCanvas.kt",
    "ui/compose/screens/HomeScreen.kt",
    "ui/compose/screens/SeismicScreen.kt",
    "ui/compose/screens/StairScreen.kt",
    "ui/compose/screens/SteelDesignScreen.kt",
]

def read(path):
    with open(path, 'r') as f:
        return f.read()

def write(path, content):
    with open(path, 'w') as f:
        f.write(content)

def fix_offset_args(content):
    """Add .toFloat() to numeric arguments inside Offset() calls."""
    fixes = 0
    
    # We need to find Offset( and add .toFloat() to numeric args
    # Strategy: find all Offset(, parse the balanced parens, fix args
    
    lines = content.split('\n')
    new_lines = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if 'Offset(' in line and 'Offset(' not in line.split('//')[0] if '//' in line else line:
            # Find Offset( positions
            offset_count = line.count('Offset(')
            if offset_count == 0:
                new_lines.append(line)
                i += 1
                continue
            
            # Process each Offset( occurrence
            new_line = line
            pos = 0
            for oc in range(offset_count):
                start = new_line.find('Offset(', pos)
                if start == -1:
                    break
                # Find matching closing paren
                depth = 1
                end = start + 7
                while end < len(new_line) and depth > 0:
                    if new_line[end] == '(':
                        depth += 1
                    elif new_line[end] == ')':
                        depth -= 1
                    end += 1
                
                # Extract the args string
                args_str = new_line[start+7:end-1]
                before = new_line[:start+7]
                after = new_line[end-1:]
                
                # Parse args and add .toFloat() to numeric literals and expressions
                # that are clearly numeric (start with digit or minus or paren)
                args = args_str.split(',')
                new_args = []
                for arg in args:
                    arg = arg.strip()
                    if not arg:
                        new_args.append(arg)
                        continue
                    # Check if arg is a numeric literal or expression that returns a number
                    # Simple heuristic: if it contains math ops and no string quotes, add .toFloat()
                    # Skip if it already has .toFloat() or if it's clearly not numeric
                    if '.toFloat()' in arg:
                        new_args.append(arg)
                    elif arg.startswith('"') or arg.startswith("'"):
                        new_args.append(arg)
                    elif re.match(r'^[0-9]', arg) or (arg.startswith('-') and len(arg) > 1 and arg[1].isdigit()):
                        new_args.append(arg + '.toFloat()')
                    elif '(' in arg and any(op in arg for op in ['+', '-', '*', '/', 'pow', 'sqrt', 'sin', 'cos', 'tan', 'max', 'min', 'abs', 'atan2']):
                        # Math expression that returns a number - add .toFloat()
                        new_args.append(f'({arg}).toFloat()')
                    else:
                        new_args.append(arg)
                
                new_line = before + ', '.join(new_args) + after
                pos = start + len(', '.join(new_args))
                fixes += 1
            
            new_lines.append(new_line)
            i += 1
        else:
            new_lines.append(line)
            i += 1
    
    return '\n'.join(new_lines), fixes

total_fixes = 0
for rel_path in ERROR_FILES:
    fpath = os.path.join(BASE, rel_path)
    if not os.path.exists(fpath):
        continue
    
    content = read(fpath)
    
    # 1. Fix Offset args
    content, f1 = fix_offset_args(content)
    
    # 2. Fix color = Color(...) -> .toArgb() (already in drawing files)
    # But let's catch it here too for non-drawing files
    content = re.sub(
        r'(\.color\s*=\s*)(Color\([^)]+)\)(?!\.)',
        r'\1\2.toArgb()',
        content
    )
    content = re.sub(
        r'(\.color\s*=\s*)(color)\b(?!\.toArgb)',
        r'\1color.toArgb()',
        content
    )
    
    f2 = content != read(fpath)
    
    if f1 or f2:
        write(fpath, content)
        print(f"  {rel_path}: offset={f1}, color={f2}")

print(f"\nTotal fixes: {total_fixes}")