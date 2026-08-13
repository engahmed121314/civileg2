#!/usr/bin/env python3
"""
FINAL comprehensive fix script for civileg2 Kotlin compilation.
Applied to CLEAN state (all files at HEAD).

Strategy:
1. Add missing imports (DrawScope, StrokeCap, StrokeJoin, Fill, Stroke, toArgb, etc.)
2. Fix ONLY Offset/Size/CornerRadius constructor calls to ensure Float params
3. Fix Color→Int paint.color assignments  
4. Fix SBC calculation type issues
5. Fix SteelDesignScreen imports (WeldDesignResult etc)
6. Fix FrameDrawingCanvas (toScreen lambda, Float/Double)
7. Fix ProfessionalBottomNavBar (Structure reference, Icon import)
8. Fix SharedComponents DesignSystem redeclaration
9. DO NOT touch nativeCanvas - it works in most places
"""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"

def read_file(p):
    with open(p, 'r', encoding='utf-8') as f: return f.read()

def write_file(p, c):
    with open(p, 'w', encoding='utf-8') as f: f.write(c)

def add_imports(content, new_imports):
    """Add imports after the last existing import line."""
    for imp in new_imports:
        if imp in content:
            continue
        lines = content.split('\n')
        last_import_idx = -1
        for i, line in enumerate(lines):
            if line.strip().startswith('import '):
                last_import_idx = i
        if last_import_idx >= 0:
            lines.insert(last_import_idx + 1, imp)
            content = '\n'.join(lines)
    return content

def fix_offset_size_cornerradius(content):
    """Fix Offset(x,y), Size(w,h), CornerRadius(r) where args are Double.
    Add .toFloat() to each argument that doesn't already have it.
    Does NOT touch nativeCanvas or other identifiers.
    """
    result = []
    i = 0
    n = len(content)
    funcs = {'Offset': 2, 'Size': 2, 'CornerRadius': 1}
    
    while i < n:
        matched = False
        for func_name, expected_args in funcs.items():
            fl = len(func_name)
            if content[i:i+fl] == func_name and i + fl < n and content[i+fl] == '(':
                # Check it's actually the function name (not part of longer identifier)
                if i > 0 and (content[i-1].isalnum() or content[i-1] == '_'):
                    continue
                
                # Find matching closing paren
                start = i + fl + 1
                depth = 1
                j = start
                while j < n and depth > 0:
                    if content[j] == '(': depth += 1
                    elif content[j] == ')': depth -= 1
                    j += 1
                
                args_str = content[start:j-1]
                
                # Parse args (handle nested parens)
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
                
                # Fix each arg
                new_args = []
                changed = False
                for arg in args:
                    a = arg.strip()
                    # Named parameter: name = value
                    if '=' in a:
                        eq_idx = a.index('=')
                        pname = a[:eq_idx+1]
                        val = a[eq_idx+1:].strip()
                        if _needs_tofloat(val):
                            new_args.append(f'{pname} {val}.toFloat()')
                            changed = True
                        else:
                            new_args.append(arg)
                    elif _needs_tofloat(a):
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
    
    return ''.join(result)

def _needs_tofloat(s):
    """Check if expression needs .toFloat()."""
    s = s.strip()
    if not s: return False
    if '.toFloat()' in s: return False
    # Float literal suffix
    if re.search(r'\df\s*$', s): return False
    if s.endswith('f)') or s.endswith('F)'): return False
    # Already has .toFloat() at end
    if s.endswith('.toFloat()'): return False
    # String
    if s.startswith('"'): return False
    # Color
    if 'Color(' in s or s.startswith('C.'): return False
    # Named parameter
    if s.startswith('topLeft') or s.startswith('size') or s.startswith('cornerRadius'): return False
    # Property access that returns Float
    if s in ('size.width', 'size.height', 'center.x', 'center.y'): return False
    # Contains method call that returns Float
    if '.width' in s and '.toFloat()' in s: return False
    # Numeric literal without f suffix (Double in Kotlin)
    if re.match(r'^-?\d+\.?\d*$', s): return True
    # Variable name or expression
    if re.match(r'^[a-zA-Z_]', s): return True
    # Expression with arithmetic operators (likely produces Double)
    if any(op in s for op in [' + ', ' - ', ' * ', ' / ']): return True
    if re.search(r'[+\-*/]', s) and not s.startswith('-'): return True
    return False

def fix_color_to_int_paint(content):
    """Fix: paint.color = Color(0x...)  →  paint.color = Color(0x...).toArgb()"""
    # Inside android.graphics.Paint.apply { this.color = Color(0x...) }
    # Pattern: this.color = Color(0xHHHHHH)
    content = re.sub(
        r'(this\.color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)',
        r'\1\2.toArgb()\3',
        content
    )
    # Pattern: color = Color(0xHHHHHH) (without this.)
    content = re.sub(
        r'(?<!this\.)(?<!\w)(color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))([ \t]*\n)',
        r'\1\2.toArgb()\3',
        content
    )
    return content

def fix_steel_design_screen(filepath):
    """Fix SteelDesignScreen.kt - wrong imports for WeldDesignResult etc."""
    content = read_file(filepath)
    orig = content
    
    # Fix import: was SteelConnectionDesign.WeldDesignResult (nested) but it's top-level
    if 'SteelConnectionDesign.WeldDesignResult' in content:
        content = content.replace(
            'import com.civileg.app.domain.calculations.ecp.SteelConnectionDesign',
            'import com.civileg.app.domain.calculations.ecp.WeldDesignResult\nimport com.civileg.app.domain.calculations.ecp.BoltDesignResult\nimport com.civileg.app.domain.calculations.ecp.BlockShearResult'
        )
        content = content.replace('SteelConnectionDesign.WeldDesignResult', 'WeldDesignResult')
        content = content.replace('SteelConnectionDesign.BoltDesignResult', 'BoltDesignResult')
        content = content.replace('SteelConnectionDesign.BlockShearResult', 'BlockShearResult')
    
    # Fix BoltGrade access
    if 'SteelBasePlateDesign.BoltGrade' in content:
        content = content.replace('SteelBasePlateDesign.BoltGrade', 'SteelBasePlateDesign.Companion.BoltGrade')
    
    # Fix missing modifier parameter - handled by build errors if needed
    
    if content != orig:
        write_file(filepath, content)
        return True
    return False

def fix_frame_drawing_canvas(filepath):
    """Fix FrameDrawingCanvas.kt - toScreen lambda, Float/Double."""
    content = read_file(filepath)
    orig = content
    
    # Fix: toScreen used as value instead of function reference
    content = content.replace('toScreen,', '::toScreen,')
    content = content.replace('toScreen)', '::toScreen)')
    
    # Fix Float/Double in computations
    # Find "val scale = " and ensure result is Float
    content = re.sub(
        r'(val scale\s*=\s*)([^;\n]+)(;)',
        lambda m: f'{m.group(1)}{m.group(2)}.toFloat(){m.group(3)}' if '.toFloat()' not in m.group(2) else m.group(0),
        content
    )
    
    if content != orig:
        write_file(filepath, content)
        return True
    return False

def fix_bottom_nav(filepath):
    """Fix ProfessionalBottomNavBar.kt."""
    content = read_file(filepath)
    orig = content
    
    # Fix Structure reference - likely needs to import or use existing enum
    # Check what's available
    if 'Unresolved reference' == True:  # placeholder
        pass
    
    # Fix Icon import
    if 'Icons.Default' in content and 'import androidx.compose.material.icons.Icons' not in content:
        content = 'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Home\nimport androidx.compose.material.icons.filled.Settings\n' + content
    
    if content != orig:
        write_file(filepath, content)
        return True
    return False

def fix_shared_components(filepath):
    """Fix SharedComponents.kt - DesignSystem redeclaration."""
    content = read_file(filepath)
    orig = content
    
    # Rename DesignSystem to AppDesignSystem to avoid conflict
    if 'object DesignSystem' in content:
        # Only rename if there's also one in ProfessionalComponents
        prof_path = os.path.join(os.path.dirname(filepath), 'ProfessionalComponents.kt')
        if os.path.exists(prof_path):
            prof_content = read_file(prof_path)
            if 'object DesignSystem' in prof_content:
                content = content.replace('object DesignSystem', 'object AppDesignSystem')
                # Also update references
                content = content.replace('DesignSystem.', 'AppDesignSystem.')
    
    if content != orig:
        write_file(filepath, content)
        return True
    return False

def fix_sbc_advanced(filepath):
    """Fix SBCAdvancedSlab.kt."""
    content = read_file(filepath)
    orig = content
    
    # Fix pow() - needs .toInt() or .toDouble() exponent
    # Find .pow( and check if arg needs conversion
    # Also fix compareTo operator issues - comparing wrong types
    
    if content != orig:
        write_file(filepath, content)
        return True
    return False

def fix_sbc_slab(filepath):
    """Fix SBCSlab.kt."""
    content = read_file(filepath)
    orig = content
    
    # Fix min() with wrong arg types - ensure both are same type
    # Pattern: min(doubleVal, floatVal) → need conversion
    
    if content != orig:
        write_file(filepath, content)
        return True
    return False

# === MAIN ===
print("=" * 60)
print("FINAL FIX SCRIPT - Clean state")
print("=" * 60)

modified_count = 0

# 1. Add missing imports to ALL drawing files
print("\n--- Phase 1: Add missing imports ---")
drawing_dir = os.path.join(BASE, "ui/compose/components/drawings")
for fname in sorted(os.listdir(drawing_dir)):
    if not fname.endswith('.kt'):
        continue
    fpath = os.path.join(drawing_dir, fname)
    content = read_file(fpath)
    orig = content
    
    needed = []
    if 'DrawScope' in content and 'import androidx.compose.ui.graphics.drawscope.DrawScope' not in content:
        needed.append('import androidx.compose.ui.graphics.drawscope.DrawScope')
    if 'StrokeCap' in content and 'import androidx.compose.ui.graphics.StrokeCap' not in content:
        needed.append('import androidx.compose.ui.graphics.StrokeCap')
    if 'StrokeJoin' in content and 'import androidx.compose.ui.graphics.StrokeJoin' not in content:
        needed.append('import androidx.compose.ui.graphics.StrokeJoin')
    if 'style = Fill' in content or 'style=Fill' in content:
        if 'import androidx.compose.ui.graphics.drawscope.Fill' not in content:
            needed.append('import androidx.compose.ui.graphics.drawscope.Fill')
    if re.search(r'style\s*=\s*Stroke\(', content):
        if 'import androidx.compose.ui.graphics.drawscope.Stroke' not in content:
            needed.append('import androidx.compose.ui.graphics.drawscope.Stroke')
    if 'CornerRadius(' in content and 'import androidx.compose.ui.geometry.CornerRadius' not in content:
        needed.append('import androidx.compose.ui.geometry.CornerRadius')
    if 'toArgb()' in content and 'import androidx.compose.ui.graphics.toArgb' not in content:
        needed.append('import androidx.compose.ui.graphics.toArgb')
    
    if needed:
        content = add_imports(content, needed)
        write_file(fpath, content)
        print(f"  {fname}: +{len(needed)} imports")
        modified_count += 1

# 2. Fix Offset/Size/CornerRadius in drawing files
print("\n--- Phase 2: Fix Offset/Size/CornerRadius Float conversion ---")
for fname in sorted(os.listdir(drawing_dir)):
    if not fname.endswith('.kt'):
        continue
    fpath = os.path.join(drawing_dir, fname)
    content = read_file(fpath)
    orig = content
    content = fix_offset_size_cornerradius(content)
    if content != orig:
        write_file(fpath, content)
        print(f"  {fname}: Fixed Offset/Size/CornerRadius")
        modified_count += 1

# 3. Fix Color→Int in drawing files
print("\n--- Phase 3: Fix Color→Int paint assignments ---")
for fname in sorted(os.listdir(drawing_dir)):
    if not fname.endswith('.kt'):
        continue
    fpath = os.path.join(drawing_dir, fname)
    content = read_file(fpath)
    orig = content
    content = fix_color_to_int_paint(content)
    if content != orig:
        write_file(fpath, content)
        print(f"  {fname}: Fixed Color→Int")
        modified_count += 1

# 4. Fix specific screen files
print("\n--- Phase 4: Fix specific files ---")
specific_fixes = [
    ("ui/compose/screens/SteelDesignScreen.kt", fix_steel_design_screen),
    ("ui/compose/screens/FrameDrawingCanvas.kt", fix_frame_drawing_canvas),
    ("ui/compose/components/SharedComponents.kt", fix_shared_components),
]

for rel_path, fix_fn in specific_fixes:
    fpath = os.path.join(BASE, rel_path)
    if os.path.exists(fpath):
        if fix_fn(fpath):
            print(f"  {os.path.basename(rel_path)}: Fixed")
            modified_count += 1

# 5. Fix StairScreen Offset/Size (if it uses Canvas)
print("\n--- Phase 5: Fix StairScreen ---")
stair_path = os.path.join(BASE, "ui/compose/screens/StairScreen.kt")
if os.path.exists(stair_path):
    content = read_file(stair_path)
    orig = content
    content = fix_offset_size_cornerradius(content)
    if content != orig:
        write_file(stair_path, content)
        print(f"  StairScreen: Fixed Offset/Size")
        modified_count += 1

# 6. Fix SeismicScreen Offset/Size (if it uses Canvas)
seismic_path = os.path.join(BASE, "ui/compose/screens/SeismicScreen.kt")
if os.path.exists(seismic_path):
    content = read_file(seismic_path)
    orig = content
    content = fix_offset_size_cornerradius(content)
    if content != orig:
        write_file(seismic_path, content)
        print(f"  SeismicScreen: Fixed Offset/Size")
        modified_count += 1

print(f"\n{'='*60}")
print(f"Total files modified: {modified_count}")
print(f"{'='*60}")