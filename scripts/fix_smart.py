#!/usr/bin/env python3
"""
Smart fix for Kotlin drawing files - handles Offset/Size/CornerRadius Double->Float,
nativeCanvas in DrawScope, Color->Int, and missing imports.
Uses a simple, robust approach.
"""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"

def read_file(p):
    with open(p, 'r', encoding='utf-8') as f: return f.read()

def write_file(p, c):
    with open(p, 'w', encoding='utf-8') as f: f.write(c)

def add_imports(text, imports):
    for imp in imports:
        if imp in text: continue
        lines = text.split('\n')
        last = -1
        for i, l in enumerate(lines):
            if l.strip().startswith('import '): last = i
        if last >= 0:
            lines.insert(last + 1, imp)
            text = '\n'.join(lines)
    return text

def fix_native_canvas_in_drawscope(text):
    """Only fix nativeCanvas inside DrawScope extension functions."""
    lines = text.split('\n')
    result = []
    in_drawscope = False
    
    for line in lines:
        # Track if we're inside a DrawScope extension function
        if re.search(r'fun\s+DrawScope\.', line):
            in_drawscope = True
        elif in_drawscope and re.match(r'^[a-z]', line) and not line.strip().startswith('//') and 'fun ' not in line and 'val ' not in line and 'var ' not in line:
            # End of function (a new declaration at column 0 that's not a statement)
            if not line[0].isspace() and not line.strip().startswith('//'):
                in_drawscope = False
        
        if in_drawscope:
            # Replace bare nativeCanvas. with drawContext.canvas.nativeCanvas.
            # But don't double-prefix
            if 'nativeCanvas.' in line and 'drawContext.canvas.nativeCanvas' not in line:
                line = line.replace('nativeCanvas.', 'drawContext.canvas.nativeCanvas.')
        
        result.append(line)
    
    return '\n'.join(result)

def fix_color_to_int(text):
    """Fix paint.color = Color(0x...) → paint.color = Color(0x...).toArgb()"""
    # Pattern: this.color = Color(0xHHHHHH) at end of line
    text = re.sub(
        r'(this\.color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)',
        r'\1\2.toArgb()\3', text)
    # Pattern: color = Color(0xHHHHHH) (not this.color)
    text = re.sub(
        r'(?<!this\.)(?<!\.)(?<!\w)(color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)',
        r'\1\2.toArgb()\3', text)
    return text

def fix_offset_size_cornerRadius(text):
    """
    Fix Offset(x, y), Size(w, h), CornerRadius(r) where args produce Double.
    Strategy: for each arg, if it doesn't end with .toFloat() and doesn't
    look like a Float literal (ends with 'f'), wrap it in .toFloat().
    We use a regex that finds these calls and processes args.
    """
    # This regex finds Offset(...), Size(...), CornerRadius(...) 
    # with balanced parens (up to 2 levels of nesting)
    pattern = r'\b(Offset|Size|CornerRadius)\(([^()]*(?:\([^()]*\)[^()]*)*)\)'
    
    def replacer(m):
        func = m.group(1)
        args_str = m.group(2)
        
        # Split args by comma at depth 0
        args = []
        current = []
        depth = 0
        for c in args_str:
            if c == '(': depth += 1; current.append(c)
            elif c == ')': depth -= 1; current.append(c)
            elif c == ',' and depth == 0:
                args.append(''.join(current))
                current = []
            else:
                current.append(c)
        if current: args.append(''.join(current))
        
        new_args = []
        changed = False
        for arg in args:
            a = arg.strip()
            # Skip named params like "topLeft = ..."
            if '=' in a:
                eq = a.index('=')
                pname = a[:eq+1]
                val = a[eq+1:].strip()
                if arg_needs_tofloat(val):
                    new_args.append(f'{pname} ({val}).toFloat()')
                    changed = True
                else:
                    new_args.append(arg)
            elif arg_needs_tofloat(a):
                new_args.append(f'({a}).toFloat()')
                changed = True
            else:
                new_args.append(arg)
        
        if changed:
            return f'{func}({", ".join(new_args)})'
        return m.group(0)
    
    return re.sub(pattern, replacer, text)

def arg_needs_tofloat(s):
    """Check if arg needs .toFloat() wrapping."""
    s = s.strip()
    if not s: return False
    # Already has .toFloat()
    if '.toFloat()' in s: return False
    # Float literal (entire string is a float like "5f" or "10.5f")
    if re.match(r'^-?\d+\.?\d*f$', s): return False
    # String literal
    if s.startswith('"'): return False
    # Contains Color()
    if 'Color(' in s: return False
    # Named param references (topLeft, size, etc.)
    if s in ('topLeft', 'size', 'cornerRadius', 'center', 'start', 'end',
             'topLeft = Offset', 'size = Size'):
        return False
    # Boolean
    if s in ('true', 'false'): return False
    # Style references
    if s.startswith('Stroke(') or s.startswith('Fill'): return False
    # PathEffect
    if 'PathEffect' in s: return False
    # Dp values
    if '.dp' in s: return False
    # .sp values
    if '.sp' in s: return False
    # If it contains any letter/underscore, it's a variable/expression that might be Double
    if re.search(r'[a-zA-Z_]', s):
        return True
    # Pure numeric without f suffix (Double literal)
    if re.match(r'^-?\d+\.?\d*$', s):
        return True
    return False

def process_drawing_file(fpath):
    content = read_file(fpath)
    orig = content
    
    # 1. Add missing imports
    needed_imports = []
    if 'StrokeCap' in content and 'import androidx.compose.ui.graphics.StrokeCap' not in content:
        needed_imports.append('import androidx.compose.ui.graphics.StrokeCap')
    if 'StrokeJoin' in content and 'import androidx.compose.ui.graphics.StrokeJoin' not in content:
        needed_imports.append('import androidx.compose.ui.graphics.StrokeJoin')
    if re.search(r'style\s*=\s*Fill\b', content) and 'import androidx.compose.ui.graphics.drawscope.Fill' not in content:
        needed_imports.append('import androidx.compose.ui.graphics.drawscope.Fill')
    if re.search(r'style\s*=\s*Stroke\(', content) and 'import androidx.compose.ui.graphics.drawscope.Stroke' not in content:
        needed_imports.append('import androidx.compose.ui.graphics.drawscope.Stroke')
    if 'toArgb()' in content and 'import androidx.compose.ui.graphics.toArgb' not in content:
        needed_imports.append('import androidx.compose.ui.graphics.toArgb')
    if 'PathEffect' in content and 'import androidx.compose.ui.graphics.PathEffect' not in content:
        needed_imports.append('import androidx.compose.ui.graphics.PathEffect')
    if 'DrawScope' in content and 'import androidx.compose.ui.graphics.drawscope.DrawScope' not in content:
        needed_imports.append('import androidx.compose.ui.graphics.drawscope.DrawScope')
    
    if needed_imports:
        content = add_imports(content, needed_imports)
    
    # 2. Fix nativeCanvas in DrawScope
    if 'nativeCanvas' in content and 'DrawScope' in content:
        content = fix_native_canvas_in_drawscope(content)
    
    # 3. Fix Offset/Size/CornerRadius
    if any(f in content for f in ['Offset(', 'Size(', 'CornerRadius(']):
        content = fix_offset_size_cornerRadius(content)
    
    # 4. Fix Color->Int in paint
    if 'Color(0x' in content:
        content = fix_color_to_int(content)
    
    if content != orig:
        write_file(fpath, content)
        return True
    return False

# === MAIN ===
print("Processing drawing files...")
drawing_dir = os.path.join(BASE, "ui/compose/components/drawings")
modified = 0
for fname in sorted(os.listdir(drawing_dir)):
    if not fname.endswith('.kt'): continue
    fpath = os.path.join(drawing_dir, fname)
    try:
        if process_drawing_file(fpath):
            print(f"  Modified: {fname}")
            modified += 1
    except Exception as e:
        print(f"  ERROR in {fname}: {e}")

print(f"\nDrawing files modified: {modified}")
print("Done.")