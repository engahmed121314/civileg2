#!/usr/bin/env python3
"""
Fix Kotlin compilation errors in Compose Canvas drawing files.
Handles: missing imports, Offset/Size/CornerRadius Float conversion,
DrawScope context, Color.toArgb, and Float/Double type mismatches.
"""
import re
import os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app"

FILES = [
    "ui/compose/components/drawings/ProfessionalColumnDrawing.kt",
    "ui/compose/components/drawings/ProfessionalFootingDrawing.kt",
    "ui/compose/components/drawings/ProfessionalSlabDrawing.kt",
    "ui/compose/components/drawings/ProfessionalRetainingWallDrawing.kt",
    "ui/compose/components/drawings/ProfessionalTankDrawing.kt",
    "ui/compose/components/drawings/ProfessionalStairDrawing.kt",
    "ui/compose/components/drawings/ProfessionalBeamDrawing.kt",
    "ui/compose/components/drawings/MomentShearForceDiagram.kt",
    "ui/compose/components/drawings/InteractiveDrawingScreen.kt",
    "ui/compose/components/drawings/DrawingUtils.kt",
    "ui/compose/screens/StairScreen.kt",
    "ui/compose/screens/ArchiveScreen.kt",
]

def read_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()

def write_file(path, content):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

def ensure_imports(content, filepath):
    """Add missing imports for Compose drawing."""
    imports_to_add = []
    
    # Check for DrawScope usage
    if 'DrawScope' in content and 'import androidx.compose.ui.graphics.drawscope.DrawScope' not in content:
        imports_to_add.append('import androidx.compose.ui.graphics.drawscope.DrawScope')
    
    # Check for drawContext usage
    if 'drawContext' in content and 'import androidx.compose.ui.graphics.drawscope.DrawContext' not in content:
        imports_to_add.append('import androidx.compose.ui.graphics.drawscope.DrawContext')
    
    # Check for StrokeCap usage
    if 'StrokeCap' in content and 'import androidx.compose.ui.graphics.StrokeCap' not in content:
        imports_to_add.append('import androidx.compose.ui.graphics.StrokeCap')
    
    # Check for StrokeJoin usage
    if 'StrokeJoin' in content and 'import androidx.compose.ui.graphics.StrokeJoin' not in content:
        imports_to_add.append('import androidx.compose.ui.graphics.StrokeJoin')
    
    # Check for Fill usage
    if 'Fill' in content and 'import androidx.compose.ui.graphics.drawscope.Fill' not in content:
        imports_to_add.append('import androidx.compose.ui.graphics.drawscope.Fill')
    
    # Check for Stroke usage (as style)
    if 'style = Stroke(' in content or 'style=Stroke(' in content:
        if 'import androidx.compose.ui.graphics.drawscope.Stroke' not in content:
            imports_to_add.append('import androidx.compose.ui.graphics.drawscope.Stroke')
    
    # Check for toArgb usage
    if '.toArgb()' in content and 'import androidx.compose.ui.graphics.toArgb' not in content:
        imports_to_add.append('import androidx.compose.ui.graphics.toArgb')
    
    # Check for math functions
    math_funcs = ['sin(', 'cos(', 'tan(', 'atan2(', 'sqrt(', 'abs(', 'log(', 'log10(', 'pow(', 'ceil(', 'floor(', 'round(']
    needs_math = any(f in content for f in math_funcs)
    if needs_math and 'import kotlin.math' not in content:
        # Add specific math imports
        needed_math = set()
        for f in ['sin', 'cos', 'tan', 'atan2', 'sqrt', 'abs', 'log', 'log10', 'pow', 'ceil', 'floor', 'round', 'PI', 'min', 'max']:
            if f'{f}(' in content or f' {f} ' in content or f'{f}.' in content:
                needed_math.add(f)
        if needed_math:
            imports_to_add.append(f"import kotlin.math.{', '.join(sorted(needed_math))}")
    
    # Check for CornerRadius
    if 'CornerRadius(' in content and 'import androidx.compose.ui.geometry.CornerRadius' not in content:
        imports_to_add.append('import androidx.compose.ui.geometry.CornerRadius')
    
    # Check for Path
    if 'Path()' in content and 'import androidx.compose.ui.graphics.Path' not in content:
        imports_to_add.append('import androidx.compose.ui.graphics.Path')
    
    if not imports_to_add:
        return content
    
    # Find insertion point (after package declaration, before first non-import line)
    lines = content.split('\n')
    insert_idx = 0
    for i, line in enumerate(lines):
        if line.startswith('package '):
            insert_idx = i + 1
            break
    
    # Skip blank lines after package
    while insert_idx < len(lines) and lines[insert_idx].strip() == '':
        insert_idx += 1
    
    # Check if imports already exist at insertion point
    for imp in list(imports_to_add):
        if imp in content:
            imports_to_add.remove(imp)
    
    if not imports_to_add:
        return content
    
    # Insert imports
    import_block = '\n'.join(imports_to_add)
    lines.insert(insert_idx, import_block)
    
    return '\n'.join(lines)


def fix_offset_size_cornerradius(content):
    """Fix Offset(), Size(), CornerRadius() calls with Double args.
    
    Strategy: Find all Offset(x, y), Size(w, h), CornerRadius(r) patterns
    and add .toFloat() to args that don't already have it.
    Be careful not to double-convert.
    """
    
    # Fix CornerRadius(x) where x might be Double
    # Pattern: CornerRadius(expr) where expr doesn't end with .toFloat() or f suffix
    def fix_cornerradius(m):
        prefix = m.group(1)
        arg = m.group(2)
        if '.toFloat()' in arg or arg.endswith('f') or arg.endswith('F'):
            return m.group(0)
        # Check if it's already a float literal like "0f" or "1.5f"
        stripped = arg.strip()
        if stripped.endswith('f') or stripped.endswith('F'):
            return m.group(0)
        return f"{prefix}CornerRadius({arg}.toFloat())"
    
    content = re.sub(
        r'(\b)CornerRadius\(([^)]+)\)',
        fix_cornerradius,
        content
    )
    
    # Fix Offset(x, y) - add .toFloat() to args that don't have it
    # We need to handle nested parens carefully
    # Simple approach: find Offset( and match balanced parens
    def fix_offset_size(match):
        func_name = match.group(1)  # Offset or Size
        args_str = match.group(2)
        
        # Split args by comma (simple split, not handling nested)
        # Actually we need to handle nested expressions
        args = split_args(args_str)
        
        fixed_args = []
        for arg in args:
            arg_s = arg.strip()
            # Skip if already has .toFloat()
            if '.toFloat()' in arg_s:
                fixed_args.append(arg)
            # Skip if already a float literal (ends with f)
            elif arg_s.endswith('f') or arg_s.endswith('F'):
                fixed_args.append(arg)
            # Skip if it's a named parameter like "width = "
            elif '=' in arg_s and not arg_s.startswith('('):
                fixed_args.append(arg)
            # Skip if it's clearly already Float (contains .0f)
            elif re.search(r'\df$', arg_s):
                fixed_args.append(arg)
            else:
                # Add .toFloat()
                fixed_args.append(f"{arg}.toFloat()")
        
        return f"{func_name}({', '.join(fixed_args)})"
    
    # Match Offset(...) and Size(...) with balanced parens
    result = []
    i = 0
    while i < len(content):
        # Look for Offset( or Size(
        for func in ['Offset(', 'Size(']:
            if content[i:i+len(func)] == func:
                # Find matching closing paren
                start = i + len(func)
                depth = 1
                j = start
                while j < len(content) and depth > 0:
                    if content[j] == '(':
                        depth += 1
                    elif content[j] == ')':
                        depth -= 1
                    j += 1
                
                args_str = content[start:j-1]
                func_name = func[:-1]  # Remove '('
                
                # Parse args
                args = split_args(args_str)
                
                fixed_args = []
                needs_fix = False
                for arg in args:
                    arg_s = arg.strip()
                    if '.toFloat()' in arg_s or arg_s.endswith('f') or arg_s.endswith('F'):
                        fixed_args.append(arg)
                    elif '=' in arg_s and not arg_s.startswith('('):
                        # Named parameter - still might need .toFloat() on the value
                        parts = arg_s.split('=', 1)
                        val = parts[1].strip()
                        if '.toFloat()' not in val and not val.endswith('f') and not val.endswith('F'):
                            fixed_args.append(f"{parts[0]}= {val}.toFloat()")
                            needs_fix = True
                        else:
                            fixed_args.append(arg)
                    elif re.search(r'\.\d+f$', arg_s):
                        fixed_args.append(arg)
                    else:
                        fixed_args.append(f"{arg}.toFloat()")
                        needs_fix = True
                
                if needs_fix:
                    result.append(f"{func_name}({', '.join(fixed_args)})")
                else:
                    result.append(content[i:j])
                i = j
                break
        else:
            result.append(content[i])
            i += 1
    
    return ''.join(result)


def split_args(s):
    """Split function arguments by comma, respecting nested parens."""
    args = []
    depth = 0
    current = []
    for c in s:
        if c == '(' or c == '[':
            depth += 1
            current.append(c)
        elif c == ')' or c == ']':
            depth -= 1
            current.append(c)
        elif c == ',' and depth == 0:
            args.append(''.join(current))
            current = []
        else:
            current.append(c)
    if current:
        args.append(''.join(current))
    return args


def fix_color_to_int(content):
    """Fix: Assignment type mismatch: Color but Int was expected."""
    # Pattern: this.color = someColor  or  color = someColor  where it should be .toArgb()
    # Also: paint.color = Color(...).hashCode() is OK, but paint.color = Color(...) is not
    
    # Fix nativeCanvas paint color assignments
    # Pattern: color = Color(...)  ->  color = Color(...).toArgb()  (when inside Paint)
    content = re.sub(
        r'(\.color\s*=\s*)(Color\([^)]*\))(\s*\n)',
        r'\1\2.toArgb()\3',
        content
    )
    
    # Fix: color = someColorVariable (not Color() constructor, not .hashCode(), not .toArgb())
    content = re.sub(
        r'(\.color\s*=\s*)([a-zA-Z][a-zA-Z0-9_]*)(\s*\n)',
        lambda m: f"{m.group(1)}{m.group(2)}.toArgb()\n" if m.group(2) not in ['color', 'textColor', 'bgColor'] and '.toArgb()' not in m.group(2) and '.hashCode()' not in m.group(2) and 'Color(' not in m.group(2) else m.group(0),
        content
    )
    
    return content


def fix_drawscope_function_refs(content):
    """Fix functions that use DrawScope methods but are outside DrawScope.
    
    If a file has private fun DrawScope.foo() but the function body calls
    drawLine, drawRect etc. as if they were top-level, they should work
    as extension functions. The real issue is the import.
    
    Also fix: functions that are NOT DrawScope extensions but call DrawScope methods.
    """
    # Check for `with(drawContext.canvas)` pattern which is wrong
    # Should be just calling drawLine etc directly in DrawScope
    
    # Fix pattern: drawContext.canvas.drawLine -> drawLine
    content = content.replace('drawContext.canvas.drawLine', 'drawLine')
    content = content.replace('drawContext.canvas.drawRect', 'drawRect')
    content = content.replace('drawContext.canvas.drawCircle', 'drawCircle')
    content = content.replace('drawContext.canvas.drawPath', 'drawPath')
    content = content.replace('drawContext.canvas.drawOval', 'drawOval')
    
    return content


def fix_stair_screen(content):
    """Fix StairScreen-specific issues."""
    # Check for unresolved references
    # stairWidth, insetWidth, cover - might be from wrong scope
    
    # Fix Offset and Size in this file specifically
    return content


def fix_missing_references(content, filepath):
    """Fix specific unresolved references."""
    basename = os.path.basename(filepath)
    
    if 'StairScreen.kt' in filepath:
        # Fix 'deleteProject' reference
        content = content.replace('deleteProject(', 'deleteSavedDesign(')
    
    if 'ArchiveScreen.kt' in filepath:
        # Fix any remaining issues
        pass
    
    return content


def fix_interactive_drawing(content):
    """Fix InteractiveDrawingScreen specific issues."""
    # Fix lambda expression issues - `x` and `y` unresolved
    # This is likely from a broken lambda like { x, y -> ... } where params are wrong
    
    # Fix InfoOutline icon reference
    if 'InfoOutline' in content and 'Icons.Default.InfoOutline' not in content and 'Icons.Outlined.Info' not in content:
        # Add the import
        if 'import androidx.compose.material.icons.Icons' not in content:
            content = content.replace(
                'import androidx.compose',
                'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Info\nimport androidx.compose',
                1
            )
        content = content.replace('Icons.Default.InfoOutline', 'Icons.Filled.Info')
    
    return content


def fix_moment_shear_diagram(content):
    """Fix MomentShearForceDiagram specific issues."""
    # Fix 'verticalArrangement' parameter
    content = re.sub(
        r'verticalArrangement\s*=\s*Arrangement\.',
        'verticalArrangement = androidx.compose.foundation.layout.Arrangement.',
        content
    )
    
    # Fix DrawScope references - if used outside Canvas
    # Replace standalone DrawScope references
    
    return content


def fix_compare_to_operator(content):
    """Fix 'operator' modifier is required on compareTo."""
    # This happens when comparing incompatible types
    # E.g., if (someDouble < someString) or similar
    # Most likely in SBC files comparing numbers with strings
    
    # In SBCAdvancedSlab.kt - fix compareTo by ensuring same types
    # Pattern: a < b where a is Double and b is String or vice versa
    # This needs specific knowledge of the file
    
    return content


def fix_sb_cfiles(content, filepath):
    """Fix SBC-specific calculation errors."""
    if 'SBCAdvancedSlab.kt' in filepath:
        # Fix pow() with wrong arg type - ensure exponent is numeric
        # Fix compareTo operator
        pass
    
    if 'SBCSlab.kt' in filepath:
        # Fix min() with wrong arg types
        pass
    
    return content


def main():
    stats = {"files_modified": 0, "total_fixes": 0}
    
    for rel_path in FILES:
        full_path = os.path.join(BASE, rel_path)
        if not os.path.exists(full_path):
            print(f"SKIP (not found): {rel_path}")
            continue
        
        print(f"\nProcessing: {rel_path}")
        original = read_file(full_path)
        content = original
        
        # Apply fixes
        prev = content
        content = ensure_imports(content, full_path)
        if content != prev:
            print(f"  [+] Added missing imports")
            stats["total_fixes"] += 1
        
        prev = content
        content = fix_offset_size_cornerradius(content)
        if content != prev:
            print(f"  [+] Fixed Offset/Size/CornerRadius Float conversion")
            stats["total_fixes"] += 1
        
        prev = content
        content = fix_color_to_int(content)
        if content != prev:
            print(f"  [+] Fixed Color-to-Int assignments")
            stats["total_fixes"] += 1
        
        prev = content
        content = fix_drawscope_function_refs(content)
        if content != prev:
            print(f"  [+] Fixed DrawScope function references")
            stats["total_fixes"] += 1
        
        prev = content
        content = fix_interactive_drawing(content)
        if content != prev:
            print(f"  [+] Fixed InteractiveDrawingScreen issues")
            stats["total_fixes"] += 1
        
        prev = content
        content = fix_moment_shear_diagram(content)
        if content != prev:
            print(f"  [+] Fixed MomentShearForceDiagram issues")
            stats["total_fixes"] += 1
        
        prev = content
        content = fix_missing_references(content, full_path)
        if content != prev:
            print(f"  [+] Fixed missing references")
            stats["total_fixes"] += 1
        
        prev = content
        content = fix_sb_cfiles(content, full_path)
        if content != prev:
            print(f"  [+] Fixed SBC calculation issues")
            stats["total_fixes"] += 1
        
        if content != original:
            write_file(full_path, content)
            stats["files_modified"] += 1
            print(f"  [SAVED] {rel_path}")
        else:
            print(f"  [NO CHANGE] {rel_path}")
    
    print(f"\n{'='*60}")
    print(f"Summary: {stats['files_modified']} files modified, {stats['total_fixes']} fix categories applied")


if __name__ == '__main__':
    main()