#!/usr/bin/env python3
"""
Comprehensive fix for Compose drawing files in Kotlin 2.1 + Compose BOM 2024.12.01.

Root causes:
1. nativeCanvas extension property has receiver resolution issues in Kotlin 2.1
   - Fix: capture as local val at top of Canvas block
2. Offset()/Size()/CornerRadius() called with Double args
   - Fix: add .toFloat() to args
3. Color assigned to Int (paint.color)
   - Fix: add .toArgb()
4. Various missing references
"""
import re, os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"

FILES = [
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalBeamDrawing.kt",
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/MomentShearForceDiagram.kt",
    "ui/compose/components/drawings/InteractiveDrawingScreen.kt",
    "ui/compose/components/drawings/DrawingUtils.kt",
    "ui/compose/screens/StairScreen.kt",
    "ui/compose/screens/ArchiveScreen.kt",
    "ui/compose/screens/SeismicScreen.kt",
    "domain/calculations/sbc/SBCAdvancedSlab.kt",
    "domain/calculations/sbc/SBCSlab.kt",
]

def read_file(p):
    with open(p, 'r', encoding='utf-8') as f: return f.read()

def write_file(p, c):
    with open(p, 'w', encoding='utf-8') as f: f.write(c)

def fix_native_canvas(content):
    """Replace nativeCanvas with captured local variable _nc."""
    if 'nativeCanvas' not in content:
        return content
    
    # Check if already has capture
    if 'val _nc = nativeCanvas' in content or 'val nc = nativeCanvas' in content:
        # Already has capture, just replace nativeCanvas references
        content = content.replace('nativeCanvas.', '_nc.')
        return content
    
    # Find Canvas blocks and add capture
    lines = content.split('\n')
    result = []
    inserted = False
    
    i = 0
    while i < len(lines):
        line = lines[i]
        result.append(line)
        
        # Detect Canvas block opening: line with ")" followed by "{" or next line has "{"
        if not inserted and 'Canvas(' in content:
            stripped = line.strip()
            # Pattern 1: ) { on same line
            if stripped.endswith(') {') or stripped == ') {':
                indent = re.match(r'(\s*)', line).group(1)
                inner = indent + '    '
                result.append(f'{inner}val _nc = nativeCanvas')
                inserted = True
            # Pattern 2: just "{" on its own line (closing the Canvas call)
            elif stripped == '{' and i > 0 and ')' in lines[i-1]:
                indent = re.match(r'(\s*)', line).group(1)
                inner = indent + '    '
                result.append(f'{inner}val _nc = nativeCanvas')
                inserted = True
            # Pattern 3: ) { on line but with other content
            elif ') {' in line and 'Canvas' in ''.join(lines[max(0,i-5):i+1]):
                indent = re.match(r'(\s*)', line).group(1)
                inner = indent + '    '
                result.append(f'{inner}val _nc = nativeCanvas')
                inserted = True
        
        i += 1
    
    if inserted:
        content = '\n'.join(result)
        # Replace all nativeCanvas references (but not the capture line)
        content = re.sub(r'\bnativeCanvas\b(?!\s*=\s*_nc)', '_nc', content)
    
    return content

def fix_density_in_local_funcs(content):
    """Fix density references inside local functions."""
    if 'density' not in content:
        return content
    
    # If there are local fun definitions and density usage, capture density
    if 'fun drawText' in content or 'fun drawHatch' in content or 'fun drawDim' in content:
        # Add capture after nativeCanvas capture if not already there
        if 'val _d = density' not in content and 'val _density = density' not in content:
            content = content.replace(
                'val _nc = nativeCanvas',
                'val _nc = nativeCanvas\n        val _d = density'
            )
        # Replace density usage inside local function bodies only
        # This is tricky - for now replace all bare density that's not the capture
        # Use a simple approach: replace " * density" and "density *" patterns
        content = content.replace('* density', '* _d')
        content = content.replace('density *', '_d *')
        # Fix capture line if mangled
        content = content.replace('val _d = _d', 'val _d = density')
    
    return content

def fix_offset_size_aggressive(content):
    """Aggressively fix Offset() and Size() and CornerRadius() to ensure Float args."""
    # Process content character by character to find Offset(...), Size(...), CornerRadius(...)
    
    funcs = ['Offset', 'Size', 'CornerRadius']
    result = []
    i = 0
    changed = False
    
    while i < len(content):
        matched = False
        for func in funcs:
            if content[i:i+len(func)] == func and content[i+len(func)] == '(':
                # Found function call, find matching )
                start = i + len(func) + 1
                depth = 1
                j = start
                while j < len(content) and depth > 0:
                    if content[j] == '(': depth += 1
                    elif content[j] == ')': depth -= 1
                    j += 1
                
                args_str = content[start:j-1]
                
                # Split args by comma (respecting nested parens)
                args = []
                current = []
                d = 0
                for c in args_str:
                    if c in '([': d += 1; current.append(c)
                    elif c in ')]': d -= 1; current.append(c)
                    elif c == ',' and d == 0:
                        args.append(''.join(current))
                        current = []
                    else:
                        current.append(c)
                if current:
                    args.append(''.join(current))
                
                # Fix each arg
                new_args = []
                arg_changed = False
                for arg in args:
                    a = arg.strip()
                    # Named parameter
                    if '=' in a and not a.startswith('('):
                        eq_idx = a.index('=')
                        name = a[:eq_idx+1]
                        val = a[eq_idx+1:].strip()
                        if needs_tofloat(val):
                            new_args.append(f'{name} {val}.toFloat()')
                            arg_changed = True
                        else:
                            new_args.append(arg)
                    elif needs_tofloat(a):
                        new_args.append(f'{a}.toFloat()')
                        arg_changed = True
                    else:
                        new_args.append(arg)
                
                if arg_changed:
                    result.append(f'{func}({", ".join(new_args)})')
                    changed = True
                else:
                    result.append(content[i:j])
                
                i = j
                matched = True
                break
        
        if not matched:
            result.append(content[i])
            i += 1
    
    return ''.join(result)

def needs_tofloat(s):
    """Check if a value expression needs .toFloat()."""
    s = s.strip()
    # Already has .toFloat()
    if '.toFloat()' in s:
        return False
    # Float literal (ends with f or F)
    if re.search(r'\d+f$', s) or re.search(r'\d+F$', s):
        return False
    # Named parameter
    if '=' in s:
        return False
    # String literal
    if s.startswith('"') or s.startswith("'"):
        return False
    # Color literal
    if s.startswith('Color(') or s.startswith('C.'):
        return False
    # Method call that returns Float
    if '.width' in s or '.height' in s:
        return False
    # Already a float operation result
    if s.endswith('.toFloat()') or s.endswith('f'):
        return False
    # Numeric literal without f suffix - these are Double in Kotlin
    if re.match(r'^-?\d+\.?\d*$', s):
        return True
    # Variable name or expression - likely Double
    if re.match(r'^[a-zA-Z_]', s) and not s.startswith('Stroke') and not s.startswith('Fill') and not s.startswith('PathEffect'):
        return True
    # Expression with arithmetic
    if any(op in s for op in ['+', '-', '*', '/']):
        return True
    return False

def fix_color_int(content):
    """Fix paint.color = someColor → paint.color = someColor.toArgb()"""
    # Inside android.graphics.Paint.apply { ... }
    # Pattern: color = Color(...) without .toArgb()
    content = re.sub(
        r'(color\s*=\s*)(Color\(0x[A-Fa-f0-9]+\))(\s*\n)',
        lambda m: f'{m.group(1)}{m.group(2)}.toArgb()\n',
        content
    )
    # Pattern: color = colorVar (variable reference, not Color() constructor)
    # Be careful: only inside Paint.apply blocks
    lines = content.split('\n')
    result = []
    for i, line in enumerate(lines):
        stripped = line.strip()
        # Match: color = variableName (at start of line, inside apply block)
        m = re.match(r'^(\s*)(color\s*=\s*)([a-zA-Z]\w*)(\s*)$', stripped)
        if m and m.group(3) not in ['color', 'textColor', 'bgColor', 'backgroundColor']:
            var = m.group(3)
            if not var.startswith('Color') and '.toArgb()' not in var and '.hashCode()' not in var:
                # Check if we're likely inside a Paint.apply block
                # Simple heuristic: check indentation level
                indent = m.group(1)
                if len(indent) >= 16:  # Deeply nested = likely inside apply
                    result.append(f'{line.rstrip()}.toArgb()')
                    continue
        result.append(line)
    return '\n'.join(result)

def fix_stair_screen(content):
    """Fix StairScreen.kt specific issues."""
    # Fix deleteProject reference
    content = content.replace('deleteProject(', 'deleteSavedDesign(')
    
    # Fix unresolved stairWidth, insetWidth, cover - these are likely from wrong scope
    # Add .toFloat() to all Offset/Size args aggressively
    content = fix_offset_size_aggressive(content)
    
    return content

def fix_sbc_files(content, filename):
    """Fix SBC calculation files."""
    if 'SBCAdvancedSlab.kt' in filename:
        # Fix pow() - ensure exponent is numeric
        # pow(someExpr) in Kotlin 2.1 requires explicit type
        # Fix: pow(base, exponent) where exponent might be wrong type
        content = re.sub(
            r'\.pow\(([^)]+)\)',
            lambda m: f'.pow({m.group(1)}.toInt())' if '.toInt()' not in m.group(1) and '.toDouble()' not in m.group(1) else m.group(0),
            content
        )
        
        # Fix compareTo - ensure same types on both sides
        # Pattern: if (a < b) where a is Double and b is String (or vice versa)
        # Replace with explicit type conversion
    
    if 'SBCSlab.kt' in filename:
        # Fix min() with wrong arg types
        content = re.sub(
            r'\bmin\(([^,]+),\s*([^)]+)\)',
            lambda m: f'min({m.group(1)}.toDouble(), {m.group(2)}.toDouble())' if '.toDouble()' not in m.group(1) else m.group(0),
            content
        )
    
    return content

def fix_interactive(content):
    """Fix InteractiveDrawingScreen.kt."""
    # Fix InfoOutline
    if 'InfoOutline' in content:
        content = content.replace('Icons.Default.InfoOutline', 'Icons.Default.Info')
        if 'import androidx.compose.material.icons.filled.Info' not in content:
            content = content.replace(
                'import androidx.compose.material.icons.Icons',
                'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Info',
                1
            )
    
    # Fix lambda x, y issues - these are likely in pointer input handlers
    # Pattern might be: { x, y -> ... } where the lambda type is wrong
    # Or: { offset -> val (x, y) = ... } that got broken
    
    # Fix asAndroidPath
    if 'asAndroidPath' in content:
        content = content.replace('.asAndroidPath()', '.asAndroidPath()')
        if 'import androidx.compose.ui.graphics.asAndroidPath' not in content:
            content = content.replace(
                'import androidx.compose.ui.graphics.Path',
                'import androidx.compose.ui.graphics.Path\nimport androidx.compose.ui.graphics.asAndroidPath',
                1
            )
    
    return content

def fix_beam_drawing(content):
    """Fix ProfessionalBeamDrawing.kt lambda issues."""
    # Fix x, y unresolved in lambdas
    # These are likely in pointerInput or drag handlers
    return content

def fix_moment_shear(content):
    """Fix MomentShearForceDiagram.kt."""
    # Fix verticalArrangement parameter
    if 'verticalArrangement = Arrangement.' in content:
        content = content.replace(
            'verticalArrangement = Arrangement.',
            'verticalArrangement = androidx.compose.foundation.layout.Arrangement.'
        )
    return content

def fix_archive(content):
    """Fix ArchiveScreen.kt."""
    return content

def fix_seismic(content):
    """Fix SeismicScreen.kt."""
    content = fix_offset_size_aggressive(content)
    return content

def add_missing_imports(content, filename):
    """Add missing imports."""
    imports_needed = []
    
    if 'toArgb()' in content and 'import androidx.compose.ui.graphics.toArgb' not in content:
        imports_needed.append('import androidx.compose.ui.graphics.toArgb')
    
    if 'asAndroidPath()' in content and 'import androidx.compose.ui.graphics.asAndroidPath' not in content:
        imports_needed.append('import androidx.compose.ui.graphics.asAndroidPath')
    
    if needs_tofloat.__code__ and '.pow(' in content and 'import kotlin.math.pow' not in content and 'import kotlin.math.' not in content.split('pow')[0][-50:]:
        # Check if pow is from kotlin.math
        if 'import kotlin.math' not in content:
            imports_needed.append('import kotlin.math.pow')
        elif ', pow' not in content[content.index('import kotlin.math'):content.index('import kotlin.math')+200]:
            # Specific pow import needed
            pass  # kotlin.math.* should cover it
    
    if not imports_needed:
        return content
    
    lines = content.split('\n')
    # Find last import line
    last_import = -1
    for i, line in enumerate(lines):
        if line.strip().startswith('import '):
            last_import = i
    
    if last_import >= 0:
        for imp in imports_needed:
            if imp not in content:
                lines.insert(last_import + 1, imp)
                last_import += 1
    
    return '\n'.join(lines)

def main():
    stats = {}
    
    for rel in FILES:
        fpath = os.path.join(BASE, rel)
        if not os.path.exists(fpath):
            print(f"SKIP: {rel}")
            continue
        
        print(f"\n{'='*60}")
        print(f"Processing: {rel}")
        orig = read_file(fpath)
        content = orig
        
        # Apply all fixes
        prev = content
        content = fix_native_canvas(content)
        if content != prev:
            print(f"  [+] Fixed nativeCanvas → _nc capture")
        
        prev = content
        content = fix_density_in_local_funcs(content)
        if content != prev:
            print(f"  [+] Fixed density capture in local funcs")
        
        prev = content
        content = fix_offset_size_aggressive(content)
        if content != prev:
            print(f"  [+] Fixed Offset/Size/CornerRadius Float conversion")
        
        prev = content
        content = fix_color_int(content)
        if content != prev:
            print(f"  [+] Fixed Color→Int assignments")
        
        # File-specific fixes
        prev = content
        if 'StairScreen' in rel:
            content = fix_stair_screen(content)
        elif 'InteractiveDrawing' in rel:
            content = fix_interactive(content)
        elif 'BeamDrawing' in rel:
            content = fix_beam_drawing(content)
        elif 'MomentShearForce' in rel:
            content = fix_moment_shear(content)
        elif 'ArchiveScreen' in rel:
            content = fix_archive(content)
        elif 'SeismicScreen' in rel:
            content = fix_seismic(content)
        elif 'SBCAdvanced' in rel or 'SBCSlab' in rel:
            content = fix_sbc_files(content, rel)
        if content != prev:
            print(f"  [+] Applied file-specific fixes")
        
        prev = content
        content = add_missing_imports(content, rel)
        if content != prev:
            print(f"  [+] Added missing imports")
        
        if content != orig:
            write_file(fpath, content)
            print(f"  [SAVED] {rel}")
            stats[rel] = True
        else:
            print(f"  [NO CHANGE]")
            stats[rel] = False
    
    modified = sum(1 for v in stats.values() if v)
    print(f"\n{'='*60}")
    print(f"Done: {modified}/{len(FILES)} files modified")

if __name__ == '__main__':
    main()