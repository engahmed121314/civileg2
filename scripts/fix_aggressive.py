#!/usr/bin/env python3
"""Aggressive fix: wrap ENTIRE Offset/Size/CornerRadius args in .toFloat().
Also fix nativeCanvas, Color->Int, and other issues."""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"

def read_file(p):
    with open(p, 'r', encoding='utf-8') as f: return f.read()

def write_file(p, c):
    with open(p, 'w', encoding='utf-8') as f: f.write(c)

def wrap_args_tofloat(text):
    """For Offset(x, y), Size(w, h), CornerRadius(r): wrap EACH arg in .toFloat()
    ONLY if the arg doesn't already end with .toFloat() and isn't obviously Float."""
    funcs = {'Offset': 2, 'Size': 2, 'CornerRadius': 1}
    result = []
    i = 0
    n = len(text)
    
    while i < n:
        matched = False
        for func_name in funcs:
            fl = len(func_name)
            if text[i:i+fl] == func_name and i + fl < n and text[i+fl] == '(':
                if i > 0 and (text[i-1].isalnum() or text[i-1] == '_'):
                    continue
                
                start = i + fl + 1
                depth = 1
                j = start
                while j < n and depth > 0:
                    if text[j] == '(': depth += 1
                    elif text[j] == ')': depth -= 1
                    j += 1
                
                args_str = text[start:j-1]
                
                # Split by comma at depth 0
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
                
                new_args = []
                changed = False
                for arg in args:
                    a = arg.strip()
                    # Named parameter
                    if '=' in a and not a.startswith('('):
                        eq_idx = a.index('=')
                        pname = a[:eq_idx+1]
                        val = a[eq_idx+1:].strip()
                        if should_wrap(val):
                            new_args.append(f'{pname} ({val}).toFloat()')
                            changed = True
                        else:
                            new_args.append(arg)
                    elif should_wrap(a):
                        new_args.append(f'({a}).toFloat()')
                        changed = True
                    else:
                        new_args.append(arg)
                
                if changed:
                    result.append(f'{func_name}({", ".join(new_args)})')
                else:
                    result.append(text[i:j])
                i = j
                matched = True
                break
        
        if not matched:
            result.append(text[i])
            i += 1
    
    return ''.join(result)

def should_wrap(s):
    """Decide if an expression should be wrapped in .toFloat()."""
    s = s.strip()
    if not s: return False
    # Already has .toFloat() at the end
    if s.endswith('.toFloat()'): return False
    # Ends with f suffix (Float literal)
    if re.search(r'[0-9]f\s*$', s): return False
    # Is a string literal
    if s.startswith('"'): return False
    # Contains Color() - don't wrap
    if 'Color(' in s: return False
    # Is just a number with f suffix inside parens or just a number
    if re.match(r'^-?\d+\.?\d*f$', s): return False
    # Already is a .toFloat() call
    if '.toFloat()' in s: return False
    # Named params
    if s.startswith('topLeft') or s.startswith('size') or s.startswith('cornerRadius'): return False
    # Skip things that are already Float-returning calls
    if re.match(r'^[a-zA-Z_]+\(.*\)\.toFloat\(\)$', s): return False
    # Everything else: wrap it
    return True

def fix_native_canvas(text):
    text = text.replace('drawContext.canvas.drawContext.canvas.nativeCanvas', 'drawContext.canvas.nativeCanvas')
    text = re.sub(r'(?<!drawContext\.canvas\.)(?<!\.)nativeCanvas\.', 'drawContext.canvas.nativeCanvas.', text)
    return text

def fix_dn(text):
    """Remove _dn references from corrupted previous fixes."""
    text = re.sub(r'\b_dn\b', 'drawContext.canvas.nativeCanvas', text)
    return text

def fix_color_to_int(text):
    text = re.sub(r'(this\.color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)', r'\1\2.toArgb()\3', text)
    text = re.sub(r'(?<!this\.)(?<!\w)(color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)', r'\1\2.toArgb()\3', text)
    return text

def add_imports(text, imports):
    for imp in imports:
        if imp in text: continue
        lines = text.split('\n')
        last_import_idx = -1
        for i, line in enumerate(lines):
            if line.strip().startswith('import '):
                last_import_idx = i
        if last_import_idx >= 0:
            lines.insert(last_import_idx + 1, imp)
            text = '\n'.join(lines)
    return text

def process_file(fpath, extra_imports=None):
    content = read_file(fpath)
    orig = content
    
    std_imports = [
        'import androidx.compose.ui.graphics.StrokeCap',
        'import androidx.compose.ui.graphics.StrokeJoin',
        'import androidx.compose.ui.graphics.drawscope.Fill',
        'import androidx.compose.ui.graphics.drawscope.Stroke',
        'import androidx.compose.ui.graphics.toArgb',
        'import androidx.compose.ui.graphics.PathEffect',
    ]
    if extra_imports:
        std_imports.extend(extra_imports)
    content = add_imports(content, std_imports)
    
    # Fix _dn corruption
    if '_dn' in content:
        content = fix_dn(content)
    
    # Fix nativeCanvas
    if 'nativeCanvas' in content and 'DrawScope' in content:
        content = fix_native_canvas(content)
    
    # Fix Offset/Size/CornerRadius - AGGRESSIVE: wrap entire args
    if any(f in content for f in ['Offset(', 'Size(', 'CornerRadius(']):
        content = wrap_args_tofloat(content)
    
    # Fix Color->Int
    if 'Color(0x' in content:
        content = fix_color_to_int(content)
    
    if content != orig:
        write_file(fpath, content)
        return True
    return False

# Process drawing files
drawing_dir = os.path.join(BASE, "ui/compose/components/drawings")
modified = 0
for fname in sorted(os.listdir(drawing_dir)):
    if not fname.endswith('.kt'): continue
    fpath = os.path.join(drawing_dir, fname)
    if process_file(fpath):
        print(f"  Fixed: {fname}")
        modified += 1

# Process other files
for rel, extra in [
    ("ui/compose/components/ProfessionalBottomNavBar.kt", ['import androidx.compose.material3.Icon']),
    ("domain/calculations/sbc/SBCAdvancedSlab.kt", ['import kotlin.math.pow']),
    ("domain/calculations/sbc/SBCSlab.kt", []),
]:
    fpath = os.path.join(BASE, rel)
    if os.path.exists(fpath) and process_file(fpath, extra):
        print(f"  Fixed: {os.path.basename(rel)}")
        modified += 1

print(f"\nTotal: {modified} files modified")