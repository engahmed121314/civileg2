#!/usr/bin/env python3
"""
Ultra-aggressive Float fix: convert ALL Double-typed local variables
inside Canvas blocks to Float at declaration time.
This fixes cascading Double→Float errors.
"""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"
DRAWING_DIR = os.path.join(BASE, "ui/compose/components/drawings")
SCREENS_DIR = os.path.join(BASE, "ui/compose/screens")

def read_file(p):
    with open(p, 'r', encoding='utf-8') as f: return f.read()

def write_file(p, c):
    with open(p, 'w', encoding='utf-8') as f: f.write(c)

def find_double_params(func_params_str):
    """Extract parameter names that are Double type."""
    params = []
    for part in func_params_str.split(','):
        part = part.strip()
        if ': Double' in part or ': Double?' in part:
            name = part.split(':')[0].strip().split()[-1]
            params.append(name)
    return params

def fix_drawing_file(filepath):
    """Fix a drawing file by converting Double vars to Float at declaration."""
    content = read_file(filepath)
    orig = content
    lines = content.split('\n')
    
    # Find @Composable function parameters that are Double
    double_params = set()
    for i, line in enumerate(lines):
        m = re.match(r'\s*(fun\s+\w+\([^)]*\))', line)
        if not m:
            # Multi-line function params
            if 'fun ' in line and '(' in line:
                # Collect params until closing paren
                params_str = line[line.index('(')+1:]
                depth = 1
                j = i + 1
                while depth > 0 and j < len(lines):
                    params_str += '\n' + lines[j]
                    depth += lines[j].count('(') - lines[j].count(')')
                    j += 1
                double_params.update(find_double_params(params_str))
    
    if not double_params:
        return False
    
    # Now find val declarations inside Canvas blocks that use these Double params
    # and add .toFloat() if they don't already have it
    result = []
    in_canvas = False
    canvas_depth = 0
    changed = False
    
    for i, line in enumerate(lines):
        # Track Canvas block depth
        if 'Canvas(' in line or 'Canvas (' in line:
            in_canvas = True
            canvas_depth = line.count('{') - line.count('}')
        
        if in_canvas:
            canvas_depth += line.count('{') - line.count('}')
        
        # Fix val declarations that produce Double
        if in_canvas and canvas_depth > 0:
            m = re.match(r'^(\s*)(val\s+(\w+)\s*=\s*)(.+?)(\s*$)', line)
            if m:
                indent = m.group(1)
                val_decl = m.group(2)
                var_name = m.group(3)
                expr = m.group(4).strip()
                trailing = m.group(5)
                
                # Skip if already has .toFloat()
                if '.toFloat()' in expr:
                    result.append(line)
                    continue
                
                # Skip if expr is a Float literal or produces Float
                if expr.endswith('f') or expr.endswith('F'):
                    result.append(line)
                    continue
                
                # Skip if val is clearly Float (from Float * Float)
                # Check if expr references any Double params
                needs_fix = False
                for dp in double_params:
                    if dp in expr:
                        needs_fix = True
                        break
                
                # Also check for common patterns that produce Double
                if not needs_fix:
                    # val x = someDouble * someFloat -> Double
                    # val x = someFloat * someDouble -> Double
                    if re.search(r'\*\s*\w+', expr) or re.search(r'\w+\s*\*', expr):
                        # Has multiplication - might be Double * Float = Double
                        # Check if any operand could be Double
                        if not expr.replace('.','').replace('-','').replace('+','').replace(' ','').isdigit():
                            needs_fix = True
                
                # Don't convert if the expr is purely Float operations
                # (heuristic: if all numeric literals have 'f' suffix and no Double params)
                if needs_fix and var_name not in ('w', 'h', 'planW', 'planH', 'margin'):
                    # Don't add .toFloat() to size/density/drawContext properties
                    if expr.strip() not in ('size.width', 'size.height', 'density'):
                        new_line = f'{indent}{val_decl}{expr}.toFloat(){trailing}'
                        result.append(new_line)
                        changed = True
                        continue
        
        result.append(line)
    
    if changed:
        write_file(filepath, '\n'.join(result))
    return changed

def fix_native_canvas_in_local_funcs(filepath):
    """Fix nativeCanvas access in local functions inside Canvas.
    
    Strategy: Find local 'fun' definitions inside Canvas blocks.
    Before the FIRST local fun, insert captures.
    Inside local fun bodies, replace nativeCanvas with captured var.
    """
    content = read_file(filepath)
    
    if 'nativeCanvas' not in content:
        return False
    
    if 'fun drawText' not in content and 'fun drawHatch' not in content and 'fun drawDim' not in content:
        # No local functions using nativeCanvas
        return False
    
    lines = content.split('\n')
    result = []
    in_canvas = False
    canvas_depth = 0
    capture_inserted = False
    changed = False
    
    for i, line in enumerate(lines):
        result.append(line)
        
        # Track Canvas block
        if 'Canvas(' in line or 'Canvas (' in line:
            in_canvas = True
            canvas_depth = line.count('{') - line.count('}')
        
        if in_canvas:
            canvas_depth += line.count('{') - line.count('}')
        
        # Detect first local function inside Canvas
        if in_canvas and canvas_depth > 0 and not capture_inserted:
            if re.match(r'\s+fun \w+\(', line):
                # This is a local function - insert capture BEFORE it
                indent = re.match(r'(\s+)', line).group(1)
                result.pop()  # Remove the fun line we just added
                
                # Insert captures
                capture_indent = indent  # Same indent as the fun
                result.append(f'{capture_indent}val _nc = nativeCanvas')
                result.append(f'{capture_indent}val _dn = density')
                result.append(line)  # Re-add the fun line
                capture_inserted = True
                changed = True
    
    if not changed:
        return False
    
    content = '\n'.join(result)
    
    # Now replace nativeCanvas and density inside local function bodies
    # Find local fun blocks and replace within them
    # Simple approach: replace nativeCanvas. with _nc. everywhere AFTER
    # the capture line, but NOT at the capture line itself
    lines = content.split('\n')
    capture_found = False
    result = []
    
    for line in lines:
        if 'val _nc = nativeCanvas' in line:
            capture_found = True
            result.append(line)
            continue
        
        if capture_found:
            # Replace nativeCanvas. with _nc. inside local functions
            # Heuristic: if indentation is deeper than Canvas block, we're in a local func
            if re.match(r'\s{12,}', line):  # 12+ spaces = inside local func
                line = line.replace('nativeCanvas.', '_nc.')
                line = line.replace(' density', ' _dn')
                # But don't replace in val _dn = density
                line = line.replace('val _dn = _dn', 'val _dn = density')
        
        result.append(line)
    
    new_content = '\n'.join(result)
    
    if new_content != content:
        write_file(filepath, new_content)
        return True
    return False


def ultra_aggressive_offset_fix(filepath):
    """Add .toFloat() to EVERY arg of Offset/Size that doesn't already have it."""
    content = read_file(filepath)
    orig = content
    
    funcs = ['Offset', 'Size', 'CornerRadius']
    result = []
    i = 0
    n = len(content)
    
    while i < n:
        matched = False
        for func_name in funcs:
            fl = len(func_name)
            if i + fl + 1 <= n and content[i:i+fl] == func_name and content[i+fl] == '(':
                # Make sure it's the function, not part of identifier
                if i > 0 and (content[i-1].isalnum() or content[i-1] == '_'):
                    continue
                
                # Find matching )
                start = i + fl + 1
                depth = 1
                j = start
                while j < n and depth > 0:
                    if content[j] == '(': depth += 1
                    elif content[j] == ')': depth -= 1
                    j += 1
                
                args_str = content[start:j-1]
                
                # Split args
                args = []
                current = []
                d = 0
                for c in args_str:
                    if c in '(': d += 1; current.append(c)
                    elif c in ')': d -= 1; current.append(c)
                    elif c == ',' and d == 0:
                        args.append(''.join(current))
                        current = []
                    else:
                        current.append(c)
                if current: args.append(''.join(current))
                
                # Process each arg
                new_args = []
                changed = False
                for arg in args:
                    a = arg.strip()
                    # Named parameter
                    if '=' in a and not a.startswith('('):
                        eq_idx = a.index('=')
                        pname = a[:eq_idx+1]
                        val = a[eq_idx+1:].strip()
                        if _should_convert(val):
                            new_args.append(f'{pname} {val}.toFloat()')
                            changed = True
                        else:
                            new_args.append(arg)
                    elif _should_convert(a):
                        new_args.append(f'{a}.toFloat()')
                        changed = True
                    else:
                        new_args.append(arg)
                
                if changed:
                    result.append(f'{func_name}({", ".join(new_args)})')
                else:
                    result.append(content[i:j])
                i = j
                matched = True
                break
        
        if not matched:
            result.append(content[i])
            i += 1
    
    return ''.join(result) != orig, ''.join(result)

def _should_convert(s):
    """Ultra-aggressive: convert anything that's not already Float."""
    s = s.strip()
    if not s: return False
    if '.toFloat()' in s: return False
    # Float literal
    if re.search(r'\df\s*$', s): return False
    if s.endswith('f)') or s.endswith('F)'): return False
    # Pure number with f suffix
    if re.match(r'^-?\d+\.?\d*f$', s): return False
    # String literal
    if s.startswith('"') or s.startswith("'"): return False
    return True  # Convert everything else


# === MAIN ===
print("=" * 60)
print("ULTRA FIX SCRIPT")
print("=" * 60)

total_modified = 0

# Phase 1: Fix Double var declarations in drawing files
print("\n--- Phase 1: Convert Double vars to Float at declaration ---")
for fname in sorted(os.listdir(DRAWING_DIR)):
    if not fname.endswith('.kt'): continue
    fpath = os.path.join(DRAWING_DIR, fname)
    if fix_drawing_file(fpath):
        print(f"  {fname}: Fixed Double vars")
        total_modified += 1

# Phase 2: Ultra-aggressive Offset/Size fix (catches remaining)
print("\n--- Phase 2: Ultra-aggressive Offset/Size fix ---")
for fname in sorted(os.listdir(DRAWING_DIR)):
    if not fname.endswith('.kt'): continue
    fpath = os.path.join(DRAWING_DIR, fname)
    changed, new_content = ultra_aggressive_offset_fix(fpath)
    if changed:
        write_file(fpath, new_content)
        print(f"  {fname}: Aggressive Offset/Size fix")
        total_modified += 1

# Phase 3: Fix nativeCanvas in local functions (only for files that need it)
print("\n--- Phase 3: Fix nativeCanvas in local functions ---")
for fname in sorted(os.listdir(DRAWING_DIR)):
    if not fname.endswith('.kt'): continue
    fpath = os.path.join(DRAWING_DIR, fname)
    if fix_native_canvas_in_local_funcs(fpath):
        print(f"  {fname}: Fixed nativeCanvas capture")
        total_modified += 1

# Phase 4: Fix screen files
print("\n--- Phase 4: Fix screen files ---")
for fname in ['StairScreen.kt', 'SeismicScreen.kt']:
    fpath = os.path.join(SCREENS_DIR, fname)
    if os.path.exists(fpath):
        changed, new_content = ultra_aggressive_offset_fix(fpath)
        if changed:
            write_file(fpath, new_content)
            print(f"  {fname}: Aggressive Offset/Size fix")
            total_modified += 1

print(f"\n{'='*60}")
print(f"Total modified: {total_modified}")