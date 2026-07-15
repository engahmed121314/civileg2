#!/usr/bin/env python3
"""Fix ProfessionalSlabDrawing.kt - Offset/Size Float, nativeCanvas, Color->Int, type issues."""
import re

FPATH = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app/ui/compose/components/drawings/ProfessionalSlabDrawing.kt"

with open(FPATH, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add missing imports
imports_to_add = [
    'import androidx.compose.ui.graphics.StrokeCap',
    'import androidx.compose.ui.graphics.StrokeJoin',
    'import androidx.compose.ui.graphics.drawscope.Fill',
    'import androidx.compose.ui.graphics.drawscope.Stroke',
    'import androidx.compose.ui.graphics.toArgb',
]
for imp in imports_to_add:
    if imp not in content:
        # Add after last import
        lines = content.split('\n')
        last_import_idx = -1
        for i, line in enumerate(lines):
            if line.strip().startswith('import '):
                last_import_idx = i
        if last_import_idx >= 0:
            lines.insert(last_import_idx + 1, imp)
            content = '\n'.join(lines)

# 2. Fix bare nativeCanvas -> drawContext.canvas.nativeCanvas inside DrawScope functions
# Only replace 'nativeCanvas.' that appears inside DrawScope extension functions
# Simple approach: replace all bare nativeCanvas. with drawContext.canvas.nativeCanvas.
# But be careful not to double-prefix already correct ones
content = content.replace('drawContext.canvas.drawContext.canvas.nativeCanvas', 'drawContext.canvas.nativeCanvas')
content = re.sub(r'(?<!drawContext\.canvas\.)(?<!\.)nativeCanvas\.', 'drawContext.canvas.nativeCanvas.', content)

# 3. Fix Color -> Int in paint.apply { this.color = Color(...) } 
content = re.sub(
    r'(this\.color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)',
    r'\1\2.toArgb()\3',
    content
)
# Also fix: color = Color(0x...) (without this.)
content = re.sub(
    r'(?<!this\.)(?<!\w)(color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)',
    r'\1\2.toArgb()\3',
    content
)

# 4. Fix Offset/Size/CornerRadius with Double args
# Strategy: find all Offset(x, y) and add .toFloat() to args that look like they could be Double
def fix_offset_size_cornerradius(text):
    """Add .toFloat() to Offset/Size/CornerRadius args where needed."""
    funcs = {'Offset': 2, 'Size': 2, 'CornerRadius': 1}
    result = []
    i = 0
    n = len(text)
    
    while i < n:
        matched = False
        for func_name, expected_args in funcs.items():
            fl = len(func_name)
            if text[i:i+fl] == func_name and i + fl < n and text[i+fl] == '(':
                # Check it's a function call, not part of longer identifier
                if i > 0 and (text[i-1].isalnum() or text[i-1] == '_'):
                    continue
                
                # Find matching paren
                start = i + fl + 1
                depth = 1
                j = start
                while j < n and depth > 0:
                    if text[j] == '(': depth += 1
                    elif text[j] == ')': depth -= 1
                    j += 1
                
                args_str = text[start:j-1]
                
                # Parse args
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
                if current:
                    args.append(''.join(current))
                
                new_args = []
                changed = False
                for arg in args:
                    a = arg.strip()
                    if '=' in a:
                        eq_idx = a.index('=')
                        pname = a[:eq_idx+1]
                        val = a[eq_idx+1:].strip()
                        if needs_tofloat(val):
                            new_args.append(f'{pname} {val}.toFloat()')
                            changed = True
                        else:
                            new_args.append(arg)
                    elif needs_tofloat(a):
                        new_args.append(f'{a}.toFloat()')
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

def needs_tofloat(s):
    s = s.strip()
    if not s: return False
    if '.toFloat()' in s: return False
    if re.search(r'\df\s*$', s): return False
    if s.endswith('f)') or s.endswith('F)'): return False
    if s.endswith('.toFloat()'): return False
    if s.startswith('"'): return False
    if 'Color(' in s or s.startswith('C.'): return False
    if s.startswith('topLeft') or s.startswith('size') or s.startswith('cornerRadius'): return False
    if s in ('size.width', 'size.height', 'center.x', 'center.y'): return False
    if re.match(r'^-?\d+\.?\d*$', s): return True
    if re.match(r'^[a-zA-Z_]', s): return True
    if any(op in s for op in [' + ', ' - ', ' * ', ' / ']): return True
    if re.search(r'[+\-*/]', s) and not s.startswith('-'): return True
    return False

content = fix_offset_size_cornerradius(content)

with open(FPATH, 'w', encoding='utf-8') as f:
    f.write(content)

print("ProfessionalSlabDrawing.kt fixed")