#!/usr/bin/env python3
"""
Fix Kotlin 2.1 compatibility: local functions inside Canvas/DrawScope blocks
cannot access DrawScope members (nativeCanvas, density, size, etc.)

Strategy: At the start of each Canvas block, capture DrawScope members into local vals.
Then inside local functions, these captured vals are accessible as closures.
"""
import re
import os

BASE = "/home/z/my-project/civileg2/app/src/main/java/com/civileg/app/ui/compose/components/drawings"

DRAWING_FILES = [
    "ProfessionalFootingDrawing.kt",
    "ProfessionalColumnDrawing.kt", 
    "ProfessionalSlabDrawing.kt",
    "ProfessionalRetainingWallDrawing.kt",
    "ProfessionalTankDrawing.kt",
    "ProfessionalStairDrawing.kt",
    "ProfessionalBeamDrawing.kt",
    "MomentShearForceDiagram.kt",
    "DrawingUtils.kt",
    "InteractiveDrawingScreen.kt",
]

def read_file(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read()

def write_file(path, content):
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

def find_canvas_blocks(content):
    """Find Canvas { } blocks and return their start/end line indices."""
    lines = content.split('\n')
    blocks = []
    for i, line in enumerate(lines):
        if 'Canvas(' in line and '{' in line:
            # Find the opening brace
            brace_idx = line.rindex('{')
            # Check if the block closes on the same line
            depth = 1
            j = i
            k = brace_idx + 1
            # Continue from this line
            remaining = line[k:]
            while depth > 0:
                for c in remaining:
                    if c == '{': depth += 1
                    elif c == '}': depth -= 1
                if depth <= 0:
                    break
                j += 1
                if j < len(lines):
                    remaining = lines[j]
                else:
                    break
            blocks.append((i, j, brace_idx))
    return blocks

def fix_canvas_local_functions(content):
    """Fix local functions inside Canvas blocks by capturing DrawScope members."""
    lines = content.split('\n')
    
    # Find all Canvas blocks
    canvas_starts = []
    for i, line in enumerate(lines):
        stripped = line.strip()
        if ('Canvas(' in line or 'Canvas (' in line) and ')' in line:
            # Check if the line has an opening brace or the next line does
            if '{' in line:
                canvas_starts.append(i)
            elif i + 1 < len(lines) and '{' in lines[i + 1]:
                canvas_starts.append(i + 1)
    
    if not canvas_starts:
        return content
    
    # For each Canvas block, find the first { and insert captures after it
    result = []
    i = 0
    modified = False
    
    while i < len(lines):
        line = lines[i]
        
        # Check if this line starts/contains a Canvas block
        is_canvas_start = False
        for cs in canvas_starts:
            if i == cs:
                is_canvas_start = True
                break
        
        if is_canvas_start:
            result.append(line)
            
            # Find where the { is
            if '{' in line:
                # Insert captures after this line
                indent = re.match(r'(\s*)', line).group(1)
                # Increase indent by 4 for inside the block
                inner_indent = indent + '    '
                
                # Check if captures already exist
                if 'val _nc = nativeCanvas' not in content:
                    captures = [
                        f"{inner_indent}val _nc = nativeCanvas",
                        f"{inner_indent}val _density = density",
                        f"{inner_indent}val _size = size",
                    ]
                    result.extend(captures)
                    modified = True
            i += 1
            continue
        
        result.append(line)
        i += 1
    
    if not modified:
        return content
    
    new_content = '\n'.join(result)
    
    # Now replace nativeCanvas/density/size references inside local functions
    # We need to find local function bodies and replace there
    # For simplicity, we'll do a targeted replacement:
    # Inside any block that has "fun " after Canvas, replace references
    
    # Actually, let's do it differently:
    # Replace patterns like:
    #   nativeCanvas.save() -> _nc.save()
    #   nativeCanvas.restore() -> _nc.restore()
    #   nativeCanvas.drawLine(...) -> _nc.drawLine(...)
    #   nativeCanvas.clipRect(...) -> _nc.clipRect(...)
    #   nativeCanvas.drawText(...) -> _nc.drawText(...)
    #   * density -> * _density (careful with this one)
    #   size.width -> _size.width
    #   size.height -> _size.height
    
    # Only replace nativeCanvas that is NOT already _nc
    new_content = re.sub(r'\bnativeCanvas\b(?!\s*=\s*_nc)', '_nc', new_content)
    
    # Replace density (only when used as a multiplier like "* density" or "density *")
    # Be careful not to replace inside val _density = density (which is the capture line)
    new_content = re.sub(r'(?<!val _\s)(?<!\w)density(?!\s*=\s*_d)(?!\w)', '_density', new_content)
    # Fix the capture line that got mangled
    new_content = new_content.replace('val __density = _density', 'val _density = density')
    new_content = new_content.replace('val _density = _density', 'val _density = density')
    
    # Replace size.width and size.height  
    new_content = new_content.replace('size.width', '_size.width')
    new_content = new_content.replace('size.height', '_size.height')
    # Fix capture line
    new_content = new_content.replace('val _size = _size', 'val _size = size')
    
    return new_content


def fix_float_double_in_drawings(content):
    """Aggressively fix Float/Double mismatches by adding .toFloat() where needed."""
    # Fix remaining Offset() calls that still have Double args
    # Pattern: Offset(expr, expr) where neither has .toFloat() and neither ends with 'f'
    
    # First, find all Offset( and Size( that might need fixing
    # This is a more aggressive version that handles all cases
    
    # Fix: strokeWidth = someDouble
    content = re.sub(r'strokeWidth\s*=\s*([a-zA-Z_]\w*\.?\w*)\s*([,*})\n])', 
                     lambda m: f'strokeWidth = {m.group(1)}.toFloat(){m.group(2)}' if '.toFloat()' not in m.group(1) and not m.group(1).endswith('f') else m.group(0),
                     content)
    
    # Fix: pathEffect = PathEffect.dashPathEffect(floatArray, someDouble)
    # Not handling this complex case
    
    return content


def fix_color_to_argb(content):
    """Fix Color assigned to Int (paint.color)."""
    # Pattern: this.color = someColorVar  (where it should be .toArgb())
    # Only inside android.graphics.Paint context
    
    # Fix: color = Color(0x...) without .toArgb() or .hashCode()
    # Already has .hashCode() in most places, but fix missing ones
    content = re.sub(
        r'(\bcolor\s*=\s*)(Color\([^)]+\))(\s*\n)',
        lambda m: f"{m.group(1)}{m.group(2)}.toArgb()\n" if '.toArgb()' not in m.group(2) and '.hashCode()' not in m.group(2) else m.group(0),
        content
    )
    
    return content


def fix_specific_files(content, filename):
    """File-specific fixes."""
    
    if 'InteractiveDrawingScreen.kt' in filename:
        # Fix lambda issues with x, y params
        # The issue is likely a broken lambda syntax
        content = content.replace('Icons.Default.InfoOutline', 'Icons.Default.Info')
        if 'import androidx.compose.material.icons.filled.Info' not in content:
            content = content.replace(
                'import androidx.compose.material.icons.Icons',
                'import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.filled.Info',
                1
            )
    
    if 'ProfessionalBeamDrawing.kt' in filename:
        # Fix lambda x, y issues
        pass
    
    if 'MomentShearForceDiagram.kt' in filename:
        # Fix verticalArrangement parameter
        content = re.sub(
            r'verticalArrangement\s*=\s*Arrangement\.',
            'verticalArrangement = androidx.compose.foundation.layout.Arrangement.',
            content
        )
    
    if 'StairScreen.kt' in filename:
        pass
    
    return content


def main():
    for fname in DRAWING_FILES:
        fpath = os.path.join(BASE, fname)
        if not os.path.exists(fpath):
            print(f"SKIP: {fname}")
            continue
        
        print(f"\nProcessing: {fname}")
        original = read_file(fpath)
        content = original
        
        # Apply fixes
        content = fix_canvas_local_functions(content)
        content = fix_float_double_in_drawings(content)
        content = fix_color_to_argb(content)
        content = fix_specific_files(content, fname)
        
        if content != original:
            write_file(fpath, content)
            print(f"  [SAVED] Changes applied")
        else:
            print(f"  [NO CHANGE]")


if __name__ == '__main__':
    main()